package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Removes stray semicolons ({@code ;}): the ones javac models as {@link EmptyStatementTree} inside
 * a block body, and the ones it models as nothing at all between the members of a class body.
 * Does not touch {@code for(;;)} loops — their empty init/condition/update are not represented as
 * {@link EmptyStatementTree} at all.
 *
 * <p>A class-body semicolon has to be found in the source, because javac's parser discards it
 * outright: {@code class S { void f() {} ; }} produces one member and no record of the {@code ;}.
 * The renderer therefore cannot emit it, and the output verifier reported the file as corrupted
 * rather than letting javafmt quietly drop a token. Deleting it here puts the source and the output
 * back in agreement.
 */
public final class EmptyStatement implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

                    @Override
                    public Void visitEmptyStatement(final EmptyStatementTree node, final Void p) {
                        if (getCurrentPath().getParentPath().getLeaf() instanceof BlockTree) {
                            final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
                            final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), node);
                            if (start >= 0 && end > start) {
                                edits.add(new LintEdit(start, end, ""));
                            }
                        }
                        return null;
                    }

                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        classBodySemicolons(node, unit).forEach(pos -> edits.add(new LintEdit(pos, pos + 1, "")));
                        return super.visitClass(node, null);
                    }
                });
    }

    /**
     * Offsets of the semicolons sitting between the members of {@code node}'s body. Enums are left
     * out: the {@code ;} that ends their constant list is a required separator, not a stray, and
     * {@link EnumTrailingComma} owns it.
     */
    private static Stream<Integer> classBodySemicolons(final ClassTree node, final ParsedUnit unit) {
        if (node.getKind() == Tree.Kind.ENUM) {
            return Stream.empty();
        }
        final var bodyOpen = bodyBrace(node, unit);
        final var classEnd = end(unit, node);
        if (bodyOpen < 0 || classEnd <= bodyOpen) {
            return Stream.empty();
        }
        final var memberSpans = node.getMembers()
                .stream()
                .filter(m -> start(unit, m) >= 0)
                .map(m -> new int[] {start(unit, m), end(unit, m),})
                .toList();
        final var source = unit.source();
        final var comments = CommentScanner.scan(source.substring(bodyOpen, classEnd));
        return IntStream.range(bodyOpen + 1, classEnd - 1)
                .filter(i -> source.charAt(i) == ';')
                .filter(i -> memberSpans.stream().noneMatch(span -> span[0] <= i && i < span[1]))
                .filter(i -> comments.stream().noneMatch(c -> bodyOpen + c.start() <= i && i < bodyOpen + c.end()))
                .boxed();
    }

    /**
     * The {@code {} that opens {@code node}'s body. Located from the end of the last header element
     * rather than from the declaration's start, so the braces in an annotation argument —
     * {@code @Marker({A, B}) class S} — are behind the cursor before the search begins.
     */
    private static int bodyBrace(final ClassTree node, final ParsedUnit unit) {
        final var headerParts = Stream.of(
                node.getModifiers().getAnnotations(),
                node.getTypeParameters(),
                node.getImplementsClause(),
                node.getPermitsClause(),
                node.getExtendsClause() == null ? List.<Tree>of() : List.of(node.getExtendsClause()))
                .flatMap(List::stream);
        final var searchFrom = headerParts.mapToInt(t -> end(unit, t))
                .filter(e -> e >= 0)
                .max()
                .orElseGet(() -> start(unit, node));
        return searchFrom < 0 ? -1 : unit.source().indexOf('{', searchFrom);
    }

    private static int start(final ParsedUnit unit, final Tree tree) {
        return (int) unit.sourcePositions().getStartPosition(unit.tree(), tree);
    }

    private static int end(final ParsedUnit unit, final Tree tree) {
        return (int) unit.sourcePositions().getEndPosition(unit.tree(), tree);
    }
}
