package io.javafmt.lint;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Enforces Java-style array notation: {@code String[] arguments} instead of C-style
 * {@code String arguments[]}, for both variables and method return types ({@code int first()[]}).
 * Detects C-style by checking whether brackets appear after the declared name in the source text
 * (C-style) vs. after the element type (Java-style).
 *
 * <p>Multi-variable declarations are skipped. javac gives every declarator in {@code int a, b[];}
 * a type span covering the whole group, so a rewrite driven off that span would swallow the other
 * declarators. {@link MultipleVariableDeclarations} splits the group first and this rule
 * normalizes the resulting single declarations on a later iteration.
 */
public final class ArrayTypeStyle implements LintRule {

    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
                edits -> new ChainPathScanner<>() {
                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        if (!MultipleVariableDeclarations.isPartOfDeclarationGroup(getCurrentPath(), unit)) {
                            fixFor(node, unit).ifPresent(edits::add);
                        }
                        return super.visitVariable(node, null);
                    }

                    @Override
                    public Void visitMethod(final MethodTree node, final Void p) {
                        fixReturnType(node, unit).ifPresent(edits::add);
                        return super.visitMethod(node, null);
                    }
                });
    }

    static boolean isCStyle(final VariableTree node, final ParsedUnit unit) {
        return fixFor(node, unit).isPresent();
    }

    private static Optional<LintEdit> fixFor(final VariableTree node, final ParsedUnit unit) {
        if (!(node.getType() instanceof ArrayTypeTree)) {
            return Optional.empty();
        }
        final var typeStart = unit.startOf(node.getType());
        final var typeEnd = unit.endOf(node.getType());
        if (typeStart < 0 || typeEnd < 0 || typeEnd > unit.source().length()) {
            return Optional.empty();
        }
        final var typeSrc = unit.source().substring(typeStart, typeEnd);
        final var name = node.getName().toString();
        final var nameIndex = indexOfCStyleName(typeSrc, name);
        if (nameIndex < 0) {
            return Optional.empty();
        }
        return bracketSuffix(typeSrc.substring(nameIndex + name.length()))
                .map(
                        suffix -> new LintEdit(
                                typeStart, typeEnd,
                                typeSrc.substring(0, nameIndex).stripTrailing() + "[]".repeat(suffix.dimensions()) + " " + name
                                        + suffix.comments().stream().map(comment -> " " + comment).collect(Collectors.joining())));
    }

    /**
     * Rewrites a C-style return type: {@code int first()[]} becomes {@code int[] first()}. javac
     * gives such a method's return-type tree a span covering the name and the parameter list too,
     * exactly as it does for a C-style variable, which is the signal.
     *
     * <p>The renderer has always printed the Java-style form regardless. Doing it here instead is
     * what lets the output verifier compare token <em>order</em>: a normalisation the renderer
     * performs on its own looks, from the verifier's side, exactly like the renderer moving code.
     */
    private static Optional<LintEdit> fixReturnType(final MethodTree node, final ParsedUnit unit) {
        if (!(node.getReturnType() instanceof ArrayTypeTree)) {
            return Optional.empty();
        }
        final var typeStart = unit.startOf(node.getReturnType());
        final var typeEnd = unit.endOf(node.getReturnType());
        if (typeStart < 0 || typeEnd < 0 || typeEnd > unit.source().length()) {
            return Optional.empty();
        }
        final var typeSrc = unit.source().substring(typeStart, typeEnd);
        final var nameIndex = indexOfCStyleName(typeSrc, node.getName().toString());
        if (nameIndex < 0) {
            return Optional.empty();
        }
        final var brackets = trailingBracketSuffix(typeSrc);
        if (brackets.isEmpty()) {
            return Optional.empty();
        }
        final var elementType = typeSrc.substring(0, nameIndex).stripTrailing();
        final var signature = typeSrc.substring(nameIndex, typeSrc.length() - brackets.length()).stripTrailing();
        return Optional.of(new LintEdit(typeStart, typeEnd, elementType + brackets + " " + signature));
    }

    /** The run of {@code []} pairs the declaration ends with, ignoring whitespace between them. */
    private static String trailingBracketSuffix(final String typeSrc) {
        var end = typeSrc.length();
        while (end > 0 && Character.isWhitespace(typeSrc.charAt(end - 1))) {
            end--;
        }
        var start = end;
        while (start >= 2 && typeSrc.charAt(start - 1) == ']' && bracketOpensAt(typeSrc, start - 2)) {
            start -= 2;
            while (start > 0 && Character.isWhitespace(typeSrc.charAt(start - 1))) {
                start--;
            }
        }
        return "[]".repeat((end - start + 1) / 2);
    }

    private static boolean bracketOpensAt(final String typeSrc, final int index) {
        return index >= 0 && typeSrc.charAt(index) == '[';
    }

    /**
     * The declared name's index within the type's own source span, or -1 if this isn't C-style.
     * javac gives a C-style {@code Type name[]} declaration's {@link ArrayTypeTree} an end
     * position spanning "{@code Type name[]}" — Java-style spans only "{@code Type[]}" — so the
     * name showing up inside the type span at all is the C-style signal. A match preceded by
     * another identifier character is the name appearing as a substring of the type name itself
     * (e.g. {@code int} inside {@code intx}) rather than as the declarator, so that's rejected too.
     */
    private static int indexOfCStyleName(final String typeSrc, final String name) {
        final var comments = CommentScanner.scan(typeSrc);
        final var nameIndex = IntStream.iterate(
                typeSrc.lastIndexOf(name), index -> index >= 0, index -> index == 0 ? -1 : typeSrc.lastIndexOf(name, index - 1))
                .filter(index -> comments.stream().noneMatch(comment -> comment.start() <= index && index < comment.end()))
                .findFirst()
                .orElse(-1);
        return nameIndex > 0 && Character.isJavaIdentifierPart(typeSrc.charAt(nameIndex - 1)) ? -1 : nameIndex;
    }

    private static Optional<BracketSuffix> bracketSuffix(final String afterName) {
        final var comments = new ArrayList<String>();
        var dimensions = 0;
        var open = false;
        var index = 0;
        while (index < afterName.length()) {
            final var character = afterName.charAt(index);
            final var commentEnd = CommentScanner.skipComment(afterName, index);
            if (commentEnd >= 0) {
                comments.add(afterName.substring(index, commentEnd).strip());
                index = commentEnd;
            } else if (Character.isWhitespace(character)) {
                index++;
            } else if (character == '[' && !open) {
                open = true;
                index++;
            } else if (character == ']' && open) {
                open = false;
                dimensions++;
                index++;
            } else {
                return Optional.empty();
            }
        }
        return open || dimensions == 0 ? Optional.empty() : Optional.of(new BracketSuffix(dimensions, List.copyOf(comments)));
    }

    private record BracketSuffix(int dimensions, List<String> comments) {}
}
