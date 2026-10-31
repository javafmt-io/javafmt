package io.javafmt.lint;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class UnusedVariableShouldBeUnnamed implements LintRule {
    private static final String UNNAMED = "_";

    private static final int FIRST_RELEASE_WITH_UNNAMED_VARIABLES = 22;

    @Override
    public LintResult apply(final LintContext context) {
        if (context.release() < FIRST_RELEASE_WITH_UNNAMED_VARIABLES) {
            return LintResult.EMPTY;
        }
        final var unit = context.unit();
        final var namesByScope = new IdentityHashMap<Tree, Set<String>>();
        return LintScan.edits(
                context,
                edits -> new ChainPathScanner<>() {
                    @Override
                    public Void visitVariable(final VariableTree node, final Void p) {
                        final var name = node.getName().toString();
                        if (!name.isEmpty() && !UNNAMED.equals(name)) {
                            scopeOfRenamable(getCurrentPath(), node, unit)
                                    .filter(
                                            scope -> !namesByScope.computeIfAbsent(scope, UnusedVariableShouldBeUnnamed::identifiersIn)
                                                    .contains(name))
                                    .flatMap(_ -> rename(node, name, unit))
                                    .ifPresent(edits::add);
                        }
                        return super.visitVariable(node, null);
                    }
                });
    }

    private static Optional<Tree> scopeOfRenamable(final TreePath path, final VariableTree node, final ParsedUnit unit) {
        final var parent = path.getParentPath();
        return switch (parent.getLeaf()) {
            case BlockTree block when node.getInitializer() != null && !MultipleVariableDeclarations.isPartOfDeclarationGroup(path, unit) ->
                Optional.of(block);
            case EnhancedForLoopTree loop when loop.getVariable() == node -> Optional.of(loop);
            case CatchTree caught -> Optional.of(caught);
            case LambdaExpressionTree lambda -> Optional.of(lambda);
            case TryTree attempt -> Optional.of(attempt);
            case BindingPatternTree _ -> Optional.of(enclosingBlockOrRoot(parent));
            default -> Optional.empty();
        };
    }

    private static Tree enclosingBlockOrRoot(final TreePath path) {
        return Stream.iterate(path, Objects::nonNull, TreePath::getParentPath)
                .map(TreePath::getLeaf)
                .filter(BlockTree.class::isInstance)
                .findFirst()
                .orElseGet(path::getCompilationUnit);
    }

    private static Optional<LintEdit> rename(final VariableTree node, final String name, final ParsedUnit unit) {
        final var start = unit.startOf(node);
        final var end = node.getInitializer() != null ? unit.startOf(node.getInitializer()) : unit.endOf(node);
        if (start < 0 || end < start) {
            return Optional.empty();
        }
        final var source = unit.source();
        return Stream.iterate(source.lastIndexOf(name, end - name.length()), at -> at >= start, at -> source.lastIndexOf(name, at - 1))
                .filter(at -> isWholeWord(source, at, name.length()))
                .findFirst()
                .filter(at -> !bracketsFollow(source, at + name.length()))
                .map(at -> new LintEdit(at, at + name.length(), UNNAMED));
    }

    private static boolean bracketsFollow(final String source, final int from) {
        return IntStream.range(from, source.length())
                .filter(i -> !Character.isWhitespace(source.charAt(i)))
                .findFirst()
                .stream()
                .anyMatch(i -> source.charAt(i) == '[');
    }

    private static boolean isWholeWord(final String source, final int at, final int length) {
        final var before = at == 0 || !Character.isJavaIdentifierPart(source.charAt(at - 1));
        final var after = at + length == source.length() || !Character.isJavaIdentifierPart(source.charAt(at + length));
        return before && after;
    }

    private static Set<String> identifiersIn(final Tree scope) {
        final var names = new HashSet<String>();
        new ChainScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void p) {
                names.add(node.getName().toString());
                return super.visitIdentifier(node, null);
            }
        }.scan(scope, null);
        return Set.copyOf(names);
    }
}
