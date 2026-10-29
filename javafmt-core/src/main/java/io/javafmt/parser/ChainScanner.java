package io.javafmt.parser;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TreeScanner} that walks a chain of binary operators, and a chain of method calls and
 * member selects, with a loop instead of one stack frame per link, so any expression javac can
 * compile can be scanned. Visits happen in the same order as {@link TreeScanner}: each operator of
 * a binary chain from the outermost in, then the leftmost operand, then the right operands from the
 * innermost out. Override {@link #visitBinaryOperator} to see each operator and {@link
 * #visitInvocation} to see each method call; {@link #visitBinary}, {@link #visitMethodInvocation}
 * and {@link #visitMemberSelect} are final.
 */
public abstract class ChainScanner<R extends @Nullable Object, P extends @Nullable Object> extends TreeScanner<R, P> {

    @Override
    @Nullable
    public final R visitBinary(final BinaryTree node, final P p) {
        final var spine = Stream.iterate(node, Objects::nonNull, b -> b.getLeftOperand() instanceof BinaryTree left ? left : null)
                .map(Objects::requireNonNull)
                .toList();
        enterChain(spine);
        try {
            spine.forEach(
                    operator -> at(
                            operator,
                            () -> {
                                visitBinaryOperator(operator, p);
                                return null;
                            }));
            final var innermost = spine.getLast();
            var result = at(innermost, () -> scan(innermost.getLeftOperand(), p));
            for (final var operator : spine.reversed()) {
                result = reduce(at(operator, () -> scan(operator.getRightOperand(), p)), result);
            }
            return result;
        } finally {
            exitChain(spine);
        }
    }

    @Override
    @Nullable
    public final R visitMethodInvocation(final MethodInvocationTree node, final P p) {
        return scanSelectorChain(node, p);
    }

    @Override
    @Nullable
    public final R visitMemberSelect(final MemberSelectTree node, final P p) {
        return scanSelectorChain(node, p);
    }

    /** Called once for each operator in a chain, before any of the chain's operands are scanned. */
    protected void visitBinaryOperator(final BinaryTree node, final P p) {}

    protected void visitInvocation(final MethodInvocationTree node, final P p) {}

    @Nullable
    private R scanSelectorChain(final Tree node, final P p) {
        final var spine = Stream.iterate(node, Objects::nonNull, ChainScanner::innerLink)
                .map(Objects::requireNonNull)
                .toList();
        enterChain(spine);
        try {
            final var typeArguments = spine.stream()
                    .map(link -> at(link, () -> link instanceof MethodInvocationTree call ? visitInvocationHead(call, p) : null))
                    .toList();
            final var innermost = spine.getLast();
            if (spine.size() == 1) {
                return scanArguments(innermost, typeArguments.getFirst(), at(innermost, () -> scan(leafOf(innermost), p)), p);
            }
            return IntStream.iterate(spine.size() - 1, i -> i >= 0, i -> i - 1)
                    .boxed()
                    .reduce(
                            at(innermost, () -> scan(leafOf(innermost), p)),
                            (inner, i) -> scanArguments(spine.get(i), typeArguments.get(i), inner, p),
                            (left, right) -> right);
        } finally {
            exitChain(spine);
        }
    }

    @Nullable
    private R scanArguments(final Tree link, @Nullable final R typeArguments, @Nullable final R inner, final P p) {
        return at(link, () -> link instanceof MethodInvocationTree call ? reduce(scan(call.getArguments(), p), reduce(inner, typeArguments)) : inner);
    }

    @Nullable
    private R visitInvocationHead(final MethodInvocationTree call, final P p) {
        visitInvocation(call, p);
        return scan(call.getTypeArguments(), p);
    }

    @Nullable
    private static Tree innerLink(final Tree link) {
        return switch (link) {
            case MethodInvocationTree call when call.getMethodSelect() instanceof MemberSelectTree select -> select;
            case MemberSelectTree select when select.getExpression() instanceof MethodInvocationTree
                || select.getExpression() instanceof MemberSelectTree ->
                select.getExpression();
            default -> null;
        };
    }

    private static Tree leafOf(final Tree link) {
        return switch (link) {
            case MethodInvocationTree call -> call.getMethodSelect();
            case MemberSelectTree select -> select.getExpression();
            default -> throw new IllegalStateException("not a chain link: " + link.getKind());
        };
    }

    void enterChain(final List<? extends Tree> spine) {}

    void exitChain(final List<? extends Tree> spine) {}

    <T extends @Nullable Object> T at(final Tree link, final Supplier<T> work) {
        return work.get();
    }
}
