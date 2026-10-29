package io.javafmt.lint;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainScanner;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

interface WarningRule extends LintRule {

    List<Diagnostic> warnings(LintContext ctx);

    @Override
    default LintResult apply(final LintContext ctx) {
        return new LintResult(List.of(), warnings(ctx));
    }

    static <T extends Tree> List<Diagnostic> onNode(
            final LintContext ctx,
            final Class<T> type,
            final Predicate<? super T> test,
            final String message) {
        return onNode(ctx, type, node -> test.test(node) ? Optional.of(message) : Optional.empty());
    }

    static <T extends Tree> List<Diagnostic> onNode(final LintContext ctx, final Class<T> type, final Function<? super T, Optional<String>> warning) {
        final var unit = ctx.unit();
        return LintScan.collect(
                ctx,
                diagnostics -> new ChainScanner<>() {

                    @Override
                    @Nullable
                    public Void scan(@Nullable final Tree tree, @Nullable final Void p) {
                        if (tree != null && !(tree instanceof BinaryTree) && !(tree instanceof MethodInvocationTree)) {
                            check(tree);
                        }
                        return super.scan(tree, null);
                    }

                    @Override
                    protected void visitBinaryOperator(final BinaryTree node, final Void p) {
                        check(node);
                    }

                    @Override
                    protected void visitInvocation(final MethodInvocationTree node, final Void p) {
                        check(node);
                    }

                    private void check(final Tree tree) {
                        if (type.isInstance(tree)) {
                            warning.apply(type.cast(tree))
                                    .ifPresent(message -> diagnostics.add(new Diagnostic.Warning(message, unit.positionOf(tree))));
                        }
                    }
                });
    }
}
