package io.github.jschneidereit.grind.lint;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.Diagnostic;
import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.jspecify.annotations.Nullable;

/**
 * Adds {@code final} to method and constructor parameters that are safe to fix, and emits
 * a warning for parameters that should be {@code final} but can't be fixed without
 * breaking compilation.
 *
 * <p>Scope: parameters of any {@link MethodTree} that has a body. Methods without a body —
 * abstract, native, and non-default interface methods — are skipped because {@code final}
 * is meaningless on a parameter the method cannot mutate. Lambda parameters and record
 * components are out of scope: lambdas live in {@code LambdaExpressionTree} (not
 * {@link MethodTree}), and record components are children of the enclosing class
 * declaration.
 *
 * <p>Following ruff's safe-fix philosophy: when a parameter is reassigned in the body,
 * adding {@code final} would turn compiling code into a compile error. Such cases produce
 * a {@link Diagnostic.Warning} instead of an edit. The developer is expected to refactor
 * (e.g. introduce a local copy or an {@link java.util.concurrent.atomic.AtomicInteger}
 * holder) and re-run grind, after which the safe edit applies.
 */
public final class FinalParameters implements LintRule {

    @Override
    public String name() {
        return "FinalParameters";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var methods = collectMethodsWithBodies(unit);
        return new LintResult(
            methods.stream().flatMap(m -> editsFor(m, unit)).toList(),
            methods.stream().flatMap(m -> warningsFor(m, unit)).toList());
    }

    private static List<MethodTree> collectMethodsWithBodies(final ParsedUnit unit) {
        final var collector = new MethodCollector();
        collector.scan(unit.tree(), null);
        return collector.methods();
    }

    private static Stream<LintEdit> editsFor(final MethodTree method, final ParsedUnit unit) {
        return method.getParameters().stream()
            .filter(p -> !hasFinal(p))
            .filter(p -> !isReassignedInBody(method.getBody(), p.getName().toString()))
            .flatMap(p -> Optional.ofNullable(editFor(p, unit)).stream());
    }

    private static Stream<Diagnostic> warningsFor(final MethodTree method, final ParsedUnit unit) {
        return method.getParameters().stream()
            .filter(p -> !hasFinal(p))
            .filter(p -> isReassignedInBody(method.getBody(), p.getName().toString()))
            .map(p -> warningFor(p, unit));
    }

    private static @Nullable LintEdit editFor(final VariableTree param, final ParsedUnit unit) {
        final var pos = (int) unit.sourcePositions().getStartPosition(unit.tree(), param);
        return pos < 0 ? null : LintEdit.insert(pos, "final ");
    }

    private static Diagnostic warningFor(final VariableTree param, final ParsedUnit unit) {
        final var message = "parameter '%s' is reassigned; cannot be made final automatically (use a local copy or AtomicInteger)".formatted(param.getName());
        return new Diagnostic.Warning(message, unit.positionOf(param));
    }

    private static boolean hasFinal(final VariableTree node) {
        return node.getModifiers().getFlags().contains(Modifier.FINAL);
    }

    private static boolean isReassignedInBody(final BlockTree body, final String name) {
        final var detector = new ReassignmentDetector(name);
        detector.scan(body, null);
        return detector.found;
    }

    private static final class MethodCollector extends TreeScanner<Void, Void> {

        private final java.util.ArrayList<MethodTree> methods = new java.util.ArrayList<>();

        @Override
        public Void visitMethod(final MethodTree node, final Void p) {
            if (node.getBody() != null) {
                methods.add(node);
            }
            return super.visitMethod(node, null);
        }

        List<MethodTree> methods() {
            return List.copyOf(methods);
        }
    }

    private static final class ReassignmentDetector extends TreeScanner<Void, Void> {

        private final String name;
        boolean found;

        ReassignmentDetector(final String name) {
            this.name = name;
        }

        @Override
        public Void visitAssignment(final AssignmentTree node, final Void p) {
            if (targetsName(node.getVariable())) {
                found = true;
            }
            return super.visitAssignment(node, null);
        }

        @Override
        public Void visitCompoundAssignment(final CompoundAssignmentTree node, final Void p) {
            if (targetsName(node.getVariable())) {
                found = true;
            }
            return super.visitCompoundAssignment(node, null);
        }

        @Override
        public Void visitUnary(final UnaryTree node, final Void p) {
            switch (node.getKind()) {
                case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> {
                    if (targetsName(node.getExpression())) {
                        found = true;
                    }
                }
                default -> { }
            }
            return super.visitUnary(node, null);
        }

        @Override
        public Void visitLambdaExpression(final LambdaExpressionTree node, final Void p) {
            return null;
        }

        @Override
        public Void visitClass(final ClassTree node, final Void p) {
            return null;
        }

        private boolean targetsName(final Tree target) {
            return target instanceof IdentifierTree id && id.getName().contentEquals(name);
        }
    }
}
