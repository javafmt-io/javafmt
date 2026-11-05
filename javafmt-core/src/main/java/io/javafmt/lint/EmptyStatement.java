package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Removes stray semicolons ({@code ;}): the ones javac models as {@link EmptyStatementTree} inside
 * a block body, and the ones it models as nothing at all between the members of a class body or
 * after the last resource of a {@code try}.
 * Does not touch {@code for(;;)} loops — their empty initializer/condition/update are not represented as
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
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
                edits -> new ChainScanner<>() {
                    @Override
                    public Void visitBlock(final BlockTree node, final Void p) {
                        node.getStatements().stream()
                                .filter(EmptyStatementTree.class::isInstance)
                                .filter(s -> unit.startOf(s) >= 0 && unit.endOf(s) > unit.startOf(s))
                                .forEach(s -> edits.add(new LintEdit(unit.startOf(s), unit.endOf(s), "")));
                        return super.visitBlock(node, null);
                    }

                    @Override
                    public Void visitTry(final TryTree node, final Void p) {
                        semicolonAfterTheLastResource(node, unit).ifPresent(pos -> edits.add(new LintEdit(pos, pos + 1, "")));
                        return super.visitTry(node, null);
                    }

                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        classBodySemicolons(node, unit).forEach(pos -> edits.add(new LintEdit(pos, pos + 1, "")));
                        return super.visitClass(node, null);
                    }
                });
    }

    private static Optional<Integer> semicolonAfterTheLastResource(final TryTree node, final ParsedUnit unit) {
        final var resources = node.getResources();
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        final var source = unit.source();
        var pos = unit.endOf(resources.getLast());
        if (pos <= 0) {
            return Optional.empty();
        }
        if (source.charAt(pos - 1) == ';') {
            return Optional.of(pos - 1);
        }
        while (pos < source.length()) {
            final var commentEnd = CommentScanner.skipComment(source, pos);
            if (commentEnd >= 0) {
                pos = commentEnd;
            } else if (Character.isWhitespace(source.charAt(pos))) {
                pos++;
            } else {
                break;
            }
        }
        return pos < source.length() && source.charAt(pos) == ';' ? Optional.of(pos) : Optional.empty();
    }

    /**
     * Offsets of the semicolons sitting between the members of {@code node}'s body. In an enum the
     * first one is skipped: it ends the constant list, a required separator rather than a stray,
     * and {@link EnumTrailingComma} owns it.
     */
    private static Stream<Integer> classBodySemicolons(final ClassTree node, final ParsedUnit unit) {
        return semicolonsBetweenMembers(node, unit).skip(node.getKind() == Tree.Kind.ENUM ? 1 : 0);
    }

    private static Stream<Integer> semicolonsBetweenMembers(final ClassTree node, final ParsedUnit unit) {
        final var bodyOpen = unit.bodyBraceOf(node);
        final var classEnd = unit.endOf(node);
        if (bodyOpen < 0 || classEnd <= bodyOpen) {
            return Stream.empty();
        }
        final var memberSpans = node.getMembers().stream()
                .filter(m -> unit.startOf(m) >= 0 && unit.endOf(m) > unit.startOf(m))
                .map(m -> new int[] {unit.startOf(m), unit.endOf(m),})
                .sorted(Comparator.comparingInt(span -> span[0]))
                .toList();
        return IntStream.rangeClosed(0, memberSpans.size())
                .boxed()
                .flatMap(
                        i -> semicolonsOutsideComments(
                                unit.source(), i == 0 ? bodyOpen + 1 : Math.max(bodyOpen + 1, memberSpans.get(i - 1)[1]),
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
}
