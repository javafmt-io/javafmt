package io.javafmt.parser;

import com.sun.source.tree.BinaryTree;
import com.sun.source.util.TreeScanner;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TreeScanner} that walks a chain of binary operators with a loop instead of one stack
 * frame per operator, so any expression javac can compile can be scanned. Visits happen in the
 * same order as {@link TreeScanner}: each operator of the chain from the outermost in, then the
 * leftmost operand, then the right operands from the innermost out. Override {@link
 * #visitBinaryOperator} to see each operator; {@link #visitBinary} is final.
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

    /** Called once for each operator in a chain, before any of the chain's operands are scanned. */
    protected void visitBinaryOperator(final BinaryTree node, final P p) {}

    void enterChain(final List<BinaryTree> spine) {}

    void exitChain(final List<BinaryTree> spine) {}

    <T extends @Nullable Object> T at(final BinaryTree operator, final Supplier<T> work) {
        return work.get();
    }
}
