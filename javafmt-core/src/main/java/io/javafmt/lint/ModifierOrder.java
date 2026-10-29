package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Enforces the Java Language Specification modifier order:
 * {@code public protected private abstract default static final sealed non-sealed transient
 * volatile synchronized native strictfp}.
 *
 * <p>An annotation before the keywords is stepped over, so {@code @Deprecated static private} is
 * reordered like any other declaration — annotated declarations are the common case, and skipping
 * them left the source disagreeing with what the renderer prints. An annotation written *between*
 * two keywords is left alone: it sits inside the span the replacement would overwrite.
 *
 * <p>Every declarator of a multi-variable declaration carries the same modifier list, so the rule
 * stays off a declaration group and waits for {@link MultipleVariableDeclarations} to split it.
 * Reordering it in place produced the same edit once per declarator, which is not a set
 * {@link LintEngine} can apply.
 *
 * <p>A comment stops the scan rather than being stepped over, for the same reason: the replacement
 * runs from the first keyword to the last one the scan reached, so continuing past a comment would
 * write over it. <code>static public /* c *&#47; final</code> therefore has only its first two keywords
 * reordered.
 */
public final class ModifierOrder implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new ChainPathScanner<>() {

                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                        return super.visitClass(node, null);
                    }

                    @Override
                    public Void visitMethod(final MethodTree node, final Void p) {
                        checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                        return super.visitMethod(node, null);
                    }

                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        if (!MultipleVariableDeclarations.isPartOfDeclarationGroup(getCurrentPath(), unit)) {
                            checkModifiers(node.getModifiers(), unit).ifPresent(edits::add);
                        }
                        return super.visitVariable(node, null);
                    }
                });
    }

    private static Optional<LintEdit> checkModifiers(final ModifiersTree modifiers, final ParsedUnit unit) {
        final var flags = modifiers.getFlags();
        final var modStart = (int) unit.sourcePositions().getStartPosition(unit.tree(), modifiers);
        if (modStart < 0) {
            return Optional.empty();
        }
        final var scan = scanModifierList(unit.source(), modStart, flags);
        if (scan.keywords().isEmpty()) {
            return Optional.empty();
        }
        final var canonicalOrder = canonicalOrderOfScannedModifiers(scan.keywords());
        if (scan.keywords().equals(canonicalOrder) && !scan.annotationAfterAKeyword()) {
            return Optional.empty();
        }
        final var rewritten = Stream.concat(
                Stream.concat(scan.annotations().stream(), scan.comments().stream()),
                canonicalOrder.stream().map(Modifier::toString));
        return Optional.of(new LintEdit(modStart, scan.lastItemEnd(), rewritten.collect(Collectors.joining(" "))));
    }

    /**
     * Walks the modifier list, collecting the annotations (as their source text) and the keywords
     * (as modifiers) in the order written.
     *
     * <p>The replacement covers everything from the start of the modifiers to the last item the
     * scan reached. Everything the scan collects is inside that span and everything inside that
     * span is written back, which is what keeps an annotation or a comment from being either
     * dropped or duplicated.
     */
    private static Scan scanModifierList(final String src, final int modStart, final Set<Modifier> flags) {
        final var annotations = new ArrayList<String>();
        final var comments = new ArrayList<String>();
        final var keywords = new ArrayList<Modifier>();
        var annotationAfterAKeyword = false;
        var lastItemEnd = -1;
        var pos = modStart;
        while (pos < src.length()) {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
            if (pos >= src.length()) {
                break;
            }
            final var commentEnd = CommentScanner.skipComment(src, pos);
            if (commentEnd >= 0) {
                if (src.startsWith("//", pos)) {
                    // A line comment runs to the end of its line, so re-emitting it inside the
                    // rewritten modifier list would swallow whatever follows. Stop instead.
                    break;
                }
                comments.add(src.substring(pos, commentEnd));
                lastItemEnd = commentEnd;
                pos = commentEnd;
                continue;
            }
            if (src.charAt(pos) == '@') {
                if (Modifiers.skipModifierKeyword(src, pos + 1, "interface") >= 0) {
                    // `@interface` is the declaration keyword, not an annotation. Hoisting it in
                    // front of the modifiers produces `@interface public`, which does not parse —
                    // twelve annotation types in java.base said so.
                    break;
                }
                final var end = Modifiers.skipAnnotation(src, pos + 1);
                annotations.add(src.substring(pos, end));
                annotationAfterAKeyword = annotationAfterAKeyword || !keywords.isEmpty();
                lastItemEnd = end;
                pos = end;
                continue;
            }
            final var matched = matchModifier(src, pos, flags);
            if (matched == null) {
                break;
            }
            pos += matched.toString().length();
            lastItemEnd = pos;
            keywords.add(matched);
        }
        return new Scan(List.copyOf(annotations), List.copyOf(comments), keywords, annotationAfterAKeyword, lastItemEnd);
    }

    /**
     * Filters the canonical order down to the modifiers the scan actually reached, preserving
     * canonical order among them. Building the replacement from {@code flags} directly would
     * append modifiers that sit past whatever stopped the scan — typically a comment — writing
     * them twice.
     */
    private static List<Modifier> canonicalOrderOfScannedModifiers(final List<Modifier> sourceOrder) {
        return Modifiers.canonicalOrder().stream().filter(sourceOrder::contains).toList();
    }

    @Nullable private static Modifier matchModifier(final String src, final int pos, final Set<Modifier> flags) {
        for (var mod : Modifiers.canonicalOrder()) {
            if (flags.contains(mod) && Modifiers.skipModifierKeyword(src, pos, mod.toString()) >= 0) {
                return mod;
            }
        }
        return null;
    }

    /**
     * @param comments block comments written among the modifiers. They are carried through the
     *     rewrite rather than stopping it, so a declaration like
     *     <code>@A /*non-public*&#47; final native @B Object m()</code> can still be normalised without
     *     losing the note. They end up after the annotations and before the keywords.
     */
    private record Scan(List<String> annotations, List<String> comments, List<Modifier> keywords, boolean annotationAfterAKeyword, int lastItemEnd) {}
}
