package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.Tree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.Comparator;
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
                edits -> new ChainScanner<>() {

                    @Override
                    public Void visitBlock(final BlockTree node, final Void p) {
                        node.getStatements()
                                .stream()
                                .filter(EmptyStatementTree.class::isInstance)
                                .filter(s -> unit.startOf(s) >= 0 && unit.endOf(s) > unit.startOf(s))
                                .forEach(s -> edits.add(new LintEdit(unit.startOf(s), unit.endOf(s), "")));
                        return super.visitBlock(node, null);
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
        final var classEnd = unit.endOf(node);
        if (bodyOpen < 0 || classEnd <= bodyOpen) {
            return Stream.empty();
        }
        final var memberSpans = node.getMembers()
                .stream()
                .filter(m -> unit.startOf(m) >= 0 && unit.endOf(m) > unit.startOf(m))
                .map(m -> new int[] {unit.startOf(m), unit.endOf(m),})
                .sorted(Comparator.comparingInt(span -> span[0]))
                .toList();
        return IntStream.rangeClosed(0, memberSpans.size())
                .boxed()
                .flatMap(
                        i -> semicolonsOutsideComments(
                                unit.source(),
                                i == 0 ? bodyOpen + 1 : Math.max(bodyOpen + 1, memberSpans.get(i - 1)[1]),
                                i == memberSpans.size() ? classEnd - 1 : memberSpans.get(i)[0]));
    }

    private static Stream<Integer> semicolonsOutsideComments(final String source, final int from, final int to) {
        if (from >= to) {
            return Stream.empty();
        }
        final var comments = CommentScanner.scan(source.substring(from, to));
        return IntStream.range(from, to)
                .filter(i -> source.charAt(i) == ';')
                .filter(i -> comments.stream().noneMatch(c -> from + c.start() <= i && i < from + c.end()))
                .boxed();
    }

    /**
     * The <code>{</code> that opens {@code node}'s body. Located from the end of the last header element
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
        final var searchFrom = headerParts.mapToInt(unit::endOf)
                .filter(e -> e >= 0)
                .max()
                .orElseGet(() -> unit.startOf(node));
        return searchFrom < 0 ? -1 : unit.source().indexOf('{', searchFrom);
    }
}
