package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.Diagnostic;
import io.javafmt.parser.JavaParser;
import java.util.List;
import java.util.stream.IntStream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class LintEngineTest {

    private static final String SOURCE = """
            class Fixture {

                int aaaabbbb = 1;
            }
            """;

    private static final int NAME_START = SOURCE.indexOf("aaaabbbb");

    private static final int DECL_START = SOURCE.indexOf("int aaaabbbb");

    static LintContext contextOf(final String source) {
        return LintContext.parseOnly(JavaParser.parseUnit(source));
    }

    @Test
    void stopsAtIterationCapWithWarning() {
        final var engine = new LintEngine(List.of(new AlwaysEdits("Runaway")));
        final var outcome = engine.lint(contextOf(SOURCE));
        assertThat(outcome.diagnostics())
                .singleElement()
                .isInstanceOf(Diagnostic.Warning.class)
                .extracting(Diagnostic::message, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("Runaway")
                .contains("10");
    }

    @Test
    void laterRuleLosesToEarlierRuleOnOverlap() {
        // Two rules want overlapping spans of the same identifier. The earlier-registered rule
        // wins the round; the later one is deferred rather than crashing the whole lint pass.
        final var earlier = new ReplaceOnce("Earlier", NAME_START, NAME_START + 8, "xxxxxxxx");
        final var later = new ReplaceOnce("Later", NAME_START + 4, NAME_START + 8, "cccc");
        final var outcome = new LintEngine(List.of(earlier, later)).lint(contextOf(SOURCE));
        assertThat(outcome.unit().source())
                .contains("int xxxxxxxx = 1;")
                .doesNotContain("cccc");
        assertThat(outcome.diagnostics()).isEmpty();
    }

    @Test
    void deferredRuleAppliesOnTheNextIteration() {
        // The loser of an overlap round must still get its chance once the winner's edit landed.
        final var earlier = new ReplaceOnce("Earlier", NAME_START, NAME_START + 8, "xxxxxxxx");
        final var later = new ReplaceOnce("Later", NAME_START + 4, NAME_START + 8, "cccc");
        final var appendsAfterWinner = new InsertUnless("Follower", DECL_START, "static ", "static");
        final var outcome = new LintEngine(List.of(earlier, later, appendsAfterWinner)).lint(contextOf(SOURCE));
        assertThat(outcome.unit().source()).contains("static int xxxxxxxx = 1;");
    }

    @Test
    void overlapWithinASingleRuleStillThrows() {
        // Self-overlapping edits are a rule bug, not rule coordination — the engine must not
        // paper over them.
        final var buggy = new SelfOverlapping("Buggy", NAME_START);
        assertThatThrownBy(() -> new LintEngine(List.of(buggy)).lint(contextOf(SOURCE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlapping lint edits");
    }

    @Test
    void insertionsAtTheSameOffsetKeepRegistrationOrder() {
        final var first = new InsertUnless("First", DECL_START, "static ", "static");
        final var second = new InsertUnless("Second", DECL_START, "final ", "final");
        final var outcome = new LintEngine(List.of(first, second)).lint(contextOf(SOURCE));
        assertThat(outcome.unit().source()).contains("static final int aaaabbbb = 1;");
    }

    @Test
    void lintAllMatchesLintFileByFile() {
        // The batch path exists only to share JavacTask setup across files. Every file's result
        // must be byte-identical to what the single-file path produces, or the optimization is a
        // behavior change in disguise.
        final var sources = List.of(SOURCE, SOURCE.replace("aaaabbbb", "cccc"), "class Other {\n\n    int y = 2;\n}\n");
        final var rules = List.<LintRule>of(new ReplaceOnce("Earlier", NAME_START, NAME_START + 8, "xxxxxxxx"));
        final var engine = new LintEngine(rules);
        final var individually = sources.stream().map(s -> engine.lint(contextOf(s)).unit().source()).toList();
        final var batched = engine.lintAll(sources.stream().map(LintEngineTest::contextOf).toList())
                .stream()
                .map(o -> ((LintEngine.BatchOutcome.Ok) o).outcome().unit().source())
                .toList();
        assertThat(batched).isEqualTo(individually);
    }

    @Test
    void lintAllReportsTheNonConvergenceWarningPerFile() {
        final var engine = new LintEngine(List.of(new AlwaysEdits("Runaway")));
        final var results = engine.lintAll(List.of(contextOf(SOURCE), contextOf(SOURCE)));
        assertThat(results)
                .hasSize(2)
                .allSatisfy(
                        result -> assertThat(((LintEngine.BatchOutcome.Ok) result).outcome().diagnostics())
                                .singleElement()
                                .isInstanceOf(Diagnostic.Warning.class));
    }

    @Test
    void aFileWhoseRewriteWillNotParseDoesNotSinkTheBatch() {
        // A lint rule that breaks syntax must isolate to its own file. The single-file path throws
        // and the caller returns that source unchanged; the batch path has to do the same without
        // taking its neighbours down.
        final var breaks = new ReplaceOnce("Breaks", NAME_START, NAME_START + 8, "not valid java");
        final var results = new LintEngine(List.of(breaks)).lintAll(List.of(contextOf(SOURCE), contextOf("class Fine {\n\n    int y = 2;\n}\n")));
        assertThat(results.getFirst()).isInstanceOf(LintEngine.BatchOutcome.Failed.class);
        assertThat(results.get(1)).isInstanceOf(LintEngine.BatchOutcome.Ok.class);
    }

    @Test
    void lintAllPreservesInputOrder() {
        final var sources = IntStream.range(0, 5)
                .mapToObj(i -> "class F" + i + " {\n\n    int v = " + i + ";\n}\n")
                .toList();
        final var results = new LintEngine(List.of(new AlwaysConverged("NoOp"))).lintAll(sources.stream().map(LintEngineTest::contextOf).toList());
        assertThat(results.stream().map(o -> ((LintEngine.BatchOutcome.Ok) o).outcome().unit().source()).toList()).isEqualTo(sources);
    }

    @Test
    void aRewriteThatWillNotParseIsJavafmtsFaultNotTheUsers() {
        // The source parsed on the way in, so a rewrite that no longer parses is a rule bug. It
        // used to surface as the ParseException javac raised, which Javafmt reports as the user's
        // own ParseError — blaming the file for javafmt's mistake.
        final var breaks = new ReplaceOnce("Breaks", NAME_START, NAME_START + 8, "not valid java");
        final var results = new LintEngine(List.of(breaks)).lintAll(List.of(contextOf(SOURCE)));
        assertThat(results.getFirst())
                .asInstanceOf(InstanceOfAssertFactories.type(LintEngine.BatchOutcome.Failed.class))
                .extracting(LintEngine.BatchOutcome.Failed::error)
                .isInstanceOf(LintRewriteException.class)
                .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                .hasMessageContaining("Breaks");
    }

    /** Emits an edit on every pass, so the engine can never reach a fixed point. */
    private record AlwaysEdits(String name) implements LintRule {

        @Override
        public LintResult apply(final LintContext ctx) {
            return LintResult.ofEdits(List.of(LintEdit.insert(ctx.unit().source().indexOf('\n') + 1, " ")));
        }
    }

    /** Replaces a fixed span, but only while the original identifier is still present. */
    private record ReplaceOnce(String name, int start, int end, String replacement) implements LintRule {

        @Override
        public LintResult apply(final LintContext ctx) {
            return ctx.unit().source().contains("aaaabbbb") ? LintResult.ofEdits(List.of(new LintEdit(start, end, replacement))) : LintResult.EMPTY;
        }
    }

    /** Inserts text at a fixed offset unless the source already contains {@code guard}. */
    private record InsertUnless(String name, int offset, String text, String guard) implements LintRule {

        @Override
        public LintResult apply(final LintContext ctx) {
            return ctx.unit().source().contains(guard) ? LintResult.EMPTY : LintResult.ofEdits(List.of(LintEdit.insert(offset, text)));
        }
    }

    /** A buggy rule: two of its own edits overlap each other. */
    private record SelfOverlapping(String name, int start) implements LintRule {

        @Override
        public LintResult apply(final LintContext ctx) {
            return ctx.unit().source().contains("aaaabbbb")
                    ? LintResult.ofEdits(List.of(new LintEdit(start, start + 8, "yyyyyyyy"), new LintEdit(start + 4, start + 8, "zzzz")))
                    : LintResult.EMPTY;
        }
    }

    /** Never emits an edit, so every file converges on the first round. */
    private record AlwaysConverged(String name) implements LintRule {

        @Override
        public LintResult apply(final LintContext ctx) {
            return LintResult.EMPTY;
        }
    }
}
