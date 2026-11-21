package io.javafmt.parser;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link TreeScanner} that walks a chain of binary operators, and a chain of method calls and
 * member selects, with a loop instead of one stack frame per link, so any expression javac can
 * compile can be scanned. Visits happen in the same order as {@link TreeScanner}: each operator of
 * a binary chain from the outermost in, then the leftmost operand, then the right operands from the
 * innermost out. Override {@link #visitBinaryOperator} to see each operator and {@link
 * #visitInvocation} to see each method call, and {@link #visitCases} to see each switch; {@link #visitBinary}, {@link #visitMethodInvocation}
 * and {@link #visitMemberSelect} are final.
 */
public abstract class ChainScanner<R extends @Nullable Object, P extends @Nullable Object> extends TreeScanner<R, P> {

    private boolean shallow;

    final void shallow() {
        shallow = true;
    }

    final boolean isShallow() {
        return shallow;
    }

    void positionedAt(final TreePath path) {}

    @Override
    @Nullable
    public R scan(@Nullable final Tree tree, @Nullable final P p) {
        return shallow ? null : super.scan(tree, p);
    }

    @Override
    @Nullable
    public final R visitBinary(final BinaryTree node, final P p) {
        final var spine = new ArrayList<BinaryTree>();
        for (var link = node; link != null; link = link.getLeftOperand() instanceof BinaryTree left ? left : null) {
            spine.add(link);
        }
        enterChain(spine);
        try {
            for (final var operator : spine) {
                at(operator, () -> {
                    visitBinaryOperator(operator, p);
                    return null;
                });
            }
            if (shallow) {
                return null;
            }
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

    @Override
    @Nullable
    public R visitSwitch(final SwitchTree node, final P p) {
        visitCases(node, node.getCases(), p);
        return super.visitSwitch(node, p);
    }

    @Override
    @Nullable
    public R visitSwitchExpression(final SwitchExpressionTree node, final P p) {
        visitCases(node, node.getCases(), p);
        return super.visitSwitchExpression(node, p);
    }

    /** Called once for each operator in a chain, before any of the chain's operands are scanned. */
    protected void visitBinaryOperator(final BinaryTree node, final P p) {}

    protected void visitInvocation(final MethodInvocationTree node, final P p) {}

    /** Called once for each switch statement and switch expression, before its cases are scanned. */
    protected void visitCases(final Tree node, final List<? extends CaseTree> cases, final P p) {}

    @Nullable
    private R scanSelectorChain(final Tree node, final P p) {
        final var spine = new ArrayList<Tree>();
        for (var link = node; link != null; link = innerLink(link)) {
            spine.add(link);
        }
        enterChain(spine);
        try {
            final var typeArguments = new ArrayList<R>(spine.size());
            for (final var link : spine) {
                typeArguments.add(at(link, () -> link instanceof MethodInvocationTree call ? visitInvocationHead(call, p) : null));
            }
            if (shallow) {
                return null;
            }
            final var innermost = spine.getLast();
            var result = at(innermost, () -> scan(leafOf(innermost), p));
            for (var i = spine.size() - 1; i >= 0; i--) {
                result = scanArguments(spine.get(i), typeArguments.get(i), result, p);
            }
            return result;
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
