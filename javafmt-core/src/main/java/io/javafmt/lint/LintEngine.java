package io.javafmt.lint;

import io.javafmt.Diagnostic;
import io.javafmt.Position;
import io.javafmt.parser.AttributedUnit;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseException;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.parser.Staged;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Runs {@link LintRule}s against a {@link LintContext}, applying their edits to the source
 * and iterating until a fixed point. The first iteration reuses the supplied parse; only
 * subsequent iterations re-parse, so clean files pay no extra parse cost.
 *
 * <p>{@link #lintAll} is the primary entry point and {@link #lint} delegates to it, so both paths
 * share one implementation. Batching exists because the re-parse between rounds dominates: task
 * setup is ~98% of a single-file parse, and almost every unformatted file needs at least one
 * round.
 *
 * <p>The initial context's attribution (if any) is held only for the first iteration. Once
 * the engine re-parses to apply an edit, the new tree no longer matches the original
 * attribution's tree paths, so subsequent iterations run with a parse-only context. A rewrite
 * that needs attribution and only becomes possible after such an edit is left to the caller,
 * which attributes the rewritten source again and lints it until an attributed pass makes no
 * edits.
 *
 * <p>Two rules can legitimately want the same span in the same iteration — {@code ModifierOrder}
 * and {@code RedundantModifier} on one modifier list, {@code MultipleVariableDeclarations} and
 * {@code ArrayTypeStyle} on one C-style declaration. Registration order breaks the tie: the
 * earlier rule's edits are applied and the later rule's are deferred to the next iteration,
 * which runs against the rewritten source. An overlap between edits of a *single* rule is still
 * a rule bug and surfaces as an {@link IllegalStateException}.
 *
 * <p>Insertions at the same offset are applied so the earlier-registered rule's text ends up
 * first, which is what makes {@code final var} compose out of two independent rules.
 *
 * <p>Diagnostics from the final iteration (the one whose edits are empty) are returned as
 * the stable, persistent set. Diagnostics from intermediate iterations are discarded
 * because the corresponding source state no longer exists after the next round of edits is
 * applied.
 */
public final class LintEngine {
    private static final int MAX_ITERATIONS = 10;

    private final List<LintRule> rules;

    public LintEngine(final List<LintRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public Outcome lint(final LintContext initial) {
        Objects.requireNonNull(initial, "initial");
        return lintAll(List.of(initial)).getFirst().orElseThrow();
    }

    /**
     * Lints every context, sharing one {@link JavaParser#parseUnits} call per round across all
     * files that still have edits to apply. A file's result is identical to what {@link #lint}
     * produces for it alone — only the javac task setup is shared.
     *
     * <p>That sharing is the entire point: a single-file parse is ~0.53 ms, of which ~98% is task
     * setup, so re-parsing one file at a time between lint rounds costs more than parsing the
     * whole batch. Since {@code FinalLocalVariable} and {@code NeedBraces} fire on almost any
     * unformatted file, nearly every real file goes through at least one round.
     *
     * <p>A rewrite that no longer parses is a rule bug, and surfaces as a {@link Staged.Failed}
     * carrying a {@link LintRewriteException} for that file only; its neighbours in the batch are
     * unaffected. It is deliberately not the {@code ParseException} javac raised: that type means
     * "the input is at fault", and the input parsed on the way in.
     */
    public List<Staged<Outcome>> lintAll(final List<LintContext> contexts) {
        Objects.requireNonNull(contexts, "contexts");
        final var states = withAttributedRewrites(contexts).stream().map(FileState::new).toList();
        for (var round = 0; round < MAX_ITERATIONS; round++) {
            final var active = states.stream().filter(FileState::isActive).toList();
            if (active.isEmpty()) {
                break;
            }
            final var lastRound = round == MAX_ITERATIONS - 1;
            active.forEach(state -> applyRound(state, lastRound));
            reparse(states.stream().filter(FileState::isActive).toList());
        }
        states.stream().filter(FileState::isActive).forEach(FileState::truncate);
        reparseConvergedAttributedFiles(states);
        return states.stream().map(FileState::result).toList();
    }

    private List<LintContext> withAttributedRewrites(final List<LintContext> contexts) {
        final var rewrites = rules.stream()
                .filter(RewriteVerifyingRule.class::isInstance)
                .map(RewriteVerifyingRule.class::cast)
                .flatMap(rule -> attributedRewrites(rule, contexts))
                .collect(
                        Collectors.groupingBy(
                                AttributedRewrite::index, Collectors.toMap(AttributedRewrite::source, AttributedRewrite::unit, (first, _) -> first)));
        return IntStream.range(0, contexts.size()).mapToObj(i -> contexts.get(i).withAttributedRewrites(rewrites.getOrDefault(i, Map.of()))).toList();
    }

    private static Stream<AttributedRewrite> attributedRewrites(final RewriteVerifyingRule rule, final List<LintContext> contexts) {
        final var wanted = IntStream.range(0, contexts.size())
                .boxed()
                .flatMap(i -> rule.rewriteToVerify(contexts.get(i).forRule(rule)).map(source -> new Rewrite(i, source)).stream())
                .collect(
                        Collectors.groupingBy(
                                rewrite -> contexts.get(rewrite.index()).attribution().orElseThrow(), IdentityHashMap::new, Collectors.toList()));
        return wanted.entrySet().stream().flatMap(batch -> attributedTogether(batch.getKey(), batch.getValue(), contexts));
    }

    private static Stream<AttributedRewrite> attributedTogether(final Attribution attribution,
                                                                final List<Rewrite> rewrites,
                                                                final List<LintContext> contexts) {
        final var batch = attribution.batch();
        final var replacements = rewrites.stream()
                .collect(Collectors.toMap(rewrite -> contexts.get(rewrite.index()).unit().source(), Rewrite::source, (first, _) -> first));
        final List<Staged<AttributedUnit>> attributed;
        try {
            attributed = attribution.session().attribute(batch.stream().map(source -> replacements.getOrDefault(source, source)).toList());
        } catch (final RuntimeException e) {
            if (attribution.session().hasEnded()) {
                throw e;
            }
            return Stream.empty();
        }
        return rewrites.stream().flatMap(rewrite -> {
            final var position = batch.indexOf(contexts.get(rewrite.index()).unit().source());
            return position >= 0 && attributed.get(position) instanceof Staged.Ok<AttributedUnit>(var unit)
                    ? Stream.of(new AttributedRewrite(rewrite.index(), rewrite.source(), unit))
                    : Stream.empty();
        });
    }

    /**
     * Converged files that were attributed still owe one parse-only re-parse, so DocBuilder never
     * sees javac-injected synthetic types. Batching these together avoids paying a parse task per
     * file individually.
     */
    private static void reparseConvergedAttributedFiles(final List<FileState> states) {
        reparse(states.stream().filter(FileState::needsParseOnlyUnit).toList());
    }

    /**
     * Runs every editing rule against one file's current source and records the edits it produced.
     * {@link WarningRule}s run only when their diagnostics are kept: on the round with no edits, or
     * on the last round before the engine gives up.
     */
    private void applyRound(final FileState state, final boolean lastRound) {
        final var editing = rules.stream().map(r -> r instanceof WarningRule ? LintResult.EMPTY : r.apply(state.context.forRule(r))).toList();
        final var edits = mergeAcrossRules(editing);
        final var results = edits.isEmpty() || lastRound ? withWarnings(editing, state.context) : editing;
        state.diagnostics = results.stream().flatMap(r -> r.diagnostics().stream()).toList();
        if (edits.isEmpty()) {
            state.converge();
            return;
        }
        state.unconvergedRules = namesOfRulesWithEdits(results);
        state.pendingSource = applyEdits(state.context.unit().source(), edits);
    }

    private List<LintResult> withWarnings(final List<LintResult> editing, final LintContext context) {
        return IntStream.range(0, rules.size())
                .mapToObj(
                        i -> rules.get(i) instanceof WarningRule warning
                                ? new LintResult(List.of(), warning.warnings(context.forRule(warning)))
                                : editing.get(i))
                .toList();
    }

    /** One {@link JavaParser#parseUnits} call for every file waiting on a re-parse this round. */
    private static void reparse(final List<FileState> waiting) {
        if (waiting.isEmpty()) {
            return;
        }
        final var outcomes = JavaParser.parseUnits(waiting.stream().map(FileState::sourceToParse).toList());
        IntStream.range(0, waiting.size()).forEach(i -> waiting.get(i).accept(outcomes.get(i)));
    }

    private List<String> namesOfRulesWithEdits(final List<LintResult> results) {
        return IntStream.range(0, rules.size()).filter(i -> !results.get(i).edits().isEmpty()).mapToObj(i -> rules.get(i).name()).toList();
    }

    private static Diagnostic nonConvergenceWarning(final List<String> unconvergedRules) {
        return new Diagnostic.Warning(
                "lint did not reach a fixed point after " + MAX_ITERATIONS + " iterations; formatting continued on the partially linted source. "
                                                                             + "Rules still producing edits: "
                        + String.join(", ", unconvergedRules),
                new Position.Unknown());
    }

    /**
     * Flattens per-rule edits in registration order, dropping a rule's edits for this iteration
     * when any of them overlaps a span an earlier rule already claimed. The whole set is dropped
     * rather than just the conflicting edit so that a rule emitting paired edits — a type
     * replacement plus the type witness that keeps it compiling, say — is never applied halfway.
     */
    static List<LintEdit> mergeAcrossRules(final List<LintResult> results) {
        return results.stream()
                .map(LintResult::edits)
                .reduce(
                        List.of(),
                        (
                                accepted,
                                candidates) -> conflicts(accepted, candidates)
                                ? accepted
                                : Stream.concat(accepted.stream(), candidates.stream()).toList());
    }

    private static boolean conflicts(final List<LintEdit> accepted, final List<LintEdit> candidates) {
        return candidates.stream().anyMatch(c -> accepted.stream().anyMatch(a -> overlaps(a, c)));
    }

    /** Half-open range intersection. Two insertions at the same offset never overlap. */
    private static boolean overlaps(final LintEdit a, final LintEdit b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static boolean isInsertion(final LintEdit edit) {
        return edit.start() == edit.end();
    }

    /**
     * Orders edit indices left to right. At a shared offset, insertions come before a replacement,
     * so the replacement's text follows them. Insertions that share an offset keep registration
     * order, which is what leaves the first-registered rule's text on the left and makes {@code
     * final var} compose out of {@code FinalLocalVariable} plus {@code LocalVarUseVar}.
     */
    private static Comparator<Integer> applicationOrder(final List<LintEdit> edits) {
        return Comparator.<Integer>comparingInt(i -> edits.get(i).start())
                .thenComparingInt(i -> isInsertion(edits.get(i)) ? 0 : 1)
                .thenComparing(Comparator.naturalOrder());
    }

    static String applyEdits(final String source, final List<LintEdit> edits) {
        final var sorted = IntStream.range(0, edits.size()).boxed().sorted(applicationOrder(edits)).map(edits::get).toList();
        IntStream.iterate(sorted.size() - 1, i -> i >= 0, i -> i - 1).forEach(i -> checkApplicable(source, sorted, i));
        final var out = new StringBuilder(source.length());
        IntStream.range(0, sorted.size())
                .forEach(i -> out.append(source, i == 0 ? 0 : sorted.get(i - 1).end(), sorted.get(i).start()).append(sorted.get(i).replacement()));
        return out.append(source, sorted.isEmpty() ? 0 : sorted.getLast().end(), source.length()).toString();
    }

    private static void checkApplicable(final String source, final List<LintEdit> sorted, final int i) {
        final var edit = sorted.get(i);
        if (i + 1 < sorted.size() && edit.end() > sorted.get(i + 1).start()) {
            throw new IllegalStateException(
                    "overlapping lint edits: edit ending at " + edit.end() + " overlaps with edit starting at " + sorted.get(i + 1).start());
        }
        if (edit.end() > source.length()) {
            throw new IllegalStateException("edit end " + edit.end() + " exceeds source length " + source.length());
        }
    }

    private record Rewrite(int index, String source) {}

    private record AttributedRewrite(int index, String source, AttributedUnit unit) {}

    /** One file's progress through the rounds. Mutable by nature: it is a loop variable per file. */
    private static final class FileState {
        private LintContext context;

        private final boolean attributed;

        @Nullable private String pendingSource;

        @Nullable private Staged<Outcome> result;

        private boolean awaitingParseOnlyUnit;

        private List<Diagnostic> diagnostics = List.of();

        private List<String> unconvergedRules = List.of();

        FileState(final LintContext initial) {
            this.context = Objects.requireNonNull(initial, "initial");
            this.attributed = initial.attribution().isPresent();
        }

        boolean isActive() {
            return result == null && !awaitingParseOnlyUnit;
        }

        boolean needsParseOnlyUnit() {
            return awaitingParseOnlyUnit;
        }

        String sourceToParse() {
            return pendingSource != null ? pendingSource : context.unit().source();
        }

        void converge() {
            if (attributed) {
                awaitingParseOnlyUnit = true;
            } else {
                result = new Staged.Ok<>(new Outcome(context.unit(), diagnostics));
            }
        }

        void truncate() {
            result = new Staged.Ok<>(
                    new Outcome(context.unit(), Stream.concat(diagnostics.stream(), Stream.of(nonConvergenceWarning(unconvergedRules))).toList()));
        }

        void accept(final Staged<ParsedUnit> reparsed) {
            switch (reparsed) {
                case Staged.Ok<ParsedUnit>(var unit) -> {
                    if (awaitingParseOnlyUnit) {
                        awaitingParseOnlyUnit = false;
                        result = new Staged.Ok<>(new Outcome(unit, diagnostics));
                    } else {
                        context = context.reparsed(unit);
                        pendingSource = null;
                    }
                }
                case Staged.Failed<ParsedUnit>(ParseException parseError) -> {
                    awaitingParseOnlyUnit = false;
                    result = new Staged.Failed<>(new LintRewriteException(unconvergedRules, parseError));
                }
                case Staged.Failed<ParsedUnit>(var failure) -> {
                    awaitingParseOnlyUnit = false;
                    result = new Staged.Failed<>(failure);
                }
            }
        }

        Staged<Outcome> result() {
            return Objects.requireNonNull(result, "result");
        }
    }

    public record Outcome(ParsedUnit unit, List<Diagnostic> diagnostics) {
        public Outcome {
            Objects.requireNonNull(unit, "unit");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
