package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

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
 * <p>A block comment among the modifiers is carried through the rewrite. A {@code //} comment
 * after a keyword stops the scan, because it runs to the end of its line and re-emitting it inside
 * the rewritten list would swallow what follows. A {@code //} comment before the first keyword, as
 * in {@code @Deprecated // since 3.1} on the line above the keywords, moves the start of the
 * replacement past it instead, so everything before it stays where it was written.
 */
public final class ModifierOrder implements LintRule {
    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
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
        final var modifierStart = unit.startOf(modifiers);
        if (modifierStart < 0) {
            return Optional.empty();
        }
        final var scan = scanModifierList(unit.source(), modifierStart, flags);
        if (scan.keywords().isEmpty()) {
            return Optional.empty();
        }
        final var canonicalOrder = canonicalOrderOfScannedModifiers(scan.keywords());
        if (scan.keywords().equals(canonicalOrder) && !scan.annotationAfterAKeyword()) {
            return Optional.empty();
        }
        final var rewritten = Stream.concat(
                Stream.concat(scan.annotations().stream(), scan.comments().stream()), canonicalOrder.stream().map(Modifier::toString));
        return Optional.of(new LintEdit(scan.start(), scan.lastItemEnd(), rewritten.collect(Collectors.joining(" "))));
    }

    /**
     * Walks the modifier list, collecting the annotations (as their source text) and the keywords
     * (as modifiers) in the order written.
     *
     * <p>The replacement covers everything from the start of the modifiers, or from the end of the
     * last {@code //} comment before the first keyword, to the last item the scan reached. Everything the scan collects is inside that span and everything inside that
     * span is written back, which is what keeps an annotation or a comment from being either
     * dropped or duplicated.
     */
    private static Scan scanModifierList(final String src, final int modifierStart, final Set<Modifier> flags) {
        final var annotations = new ArrayList<String>();
        final var comments = new ArrayList<String>();
        final var keywords = new ArrayList<Modifier>();
        var annotationAfterAKeyword = false;
        var start = modifierStart;
        var lastItemEnd = -1;
        var pos = modifierStart;
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
                    if (!keywords.isEmpty()) {
                        break;
                    }
                    annotations.clear();
                    comments.clear();
                    start = -1;
                    lastItemEnd = -1;
                    pos = commentEnd;
                    continue;
                }
                start = start < 0 ? pos : start;
                comments.add(src.substring(pos, commentEnd));
                lastItemEnd = commentEnd;
                pos = commentEnd;
                continue;
            }
            start = start < 0 ? pos : start;
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
        return new Scan(start, List.copyOf(annotations), List.copyOf(comments), keywords, annotationAfterAKeyword, lastItemEnd);
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

    @Nullable
    private static Modifier matchModifier(final String src, final int pos, final Set<Modifier> flags) {
        for (final var modifier : Modifiers.canonicalOrder()) {
            if (flags.contains(modifier) && Modifiers.skipModifierKeyword(src, pos, modifier.toString()) >= 0) {
                return modifier;
            }
        }
        return null;
    }

    /**
     * @param start where the replacement begins
     * @param comments block comments written among the modifiers. They are carried through the
     *     rewrite rather than stopping it, so a declaration like
     *     <code>@A /*non-public*&#47; final native @B Object m()</code> can still be normalised without
     *     losing the note. They end up after the annotations and before the keywords.
     */
    private record Scan(int start, List<String> annotations, List<String> comments, List<Modifier> keywords, boolean annotationAfterAKeyword, int lastItemEnd) {}
}
