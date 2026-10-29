package io.javafmt.lint;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;
import io.javafmt.parser.TreeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
 * holder) and re-run javafmt, after which the safe edit applies.
 */
public final class FinalParameters implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var facts = new TreeFacts(unit);
        final var methods = collectMethodsWithBodies(unit, facts);
        return new LintResult(
                methods.stream()
                        .flatMap(m -> editsFor(m, unit))
                        .toList(),
                methods.stream().flatMap(m -> warningsFor(m, unit)).toList());
    }

    /**
     * Methods and constructors with a body, excluding compact constructors: their parameters are
     * the record's header components, and the JLS forbids modifiers there, so adding {@code
     * final} would break the re-parse.
     */
    private static List<MethodTree> collectMethodsWithBodies(final ParsedUnit unit, final TreeFacts facts) {
        final var collector = new MethodCollector();
        collector.scan(unit.tree(), null);
        return collector.methods()
                .stream()
                .filter(m -> !facts.isCompactConstructor(m))
                .toList();
    }

    private static Stream<LintEdit> editsFor(final MethodTree method, final ParsedUnit unit) {
        return method.getParameters()
                .stream()
                .filter(p -> !Modifiers.hasFinal(p))
                .filter(p -> !Modifiers.reassigned(method.getBody(), p.getName().toString()))
                .flatMap(p -> Optional.ofNullable(editFor(p, unit)).stream());
    }

    private static Stream<Diagnostic> warningsFor(final MethodTree method, final ParsedUnit unit) {
        return method.getParameters()
                .stream()
                .filter(p -> !Modifiers.hasFinal(p))
                .filter(p -> Modifiers.reassigned(method.getBody(), p.getName().toString()))
                .map(p -> warningFor(p, unit));
    }

    @Nullable private static LintEdit editFor(final VariableTree param, final ParsedUnit unit) {
        final var pos = (int) unit.sourcePositions().getStartPosition(unit.tree(), param);
        return pos < 0 ? null : LintEdit.insert(pos, "final ");
    }

    private static Diagnostic warningFor(final VariableTree param, final ParsedUnit unit) {
        final var message = "parameter '%s' is reassigned; cannot be made final automatically (use a local copy or AtomicInteger)".formatted(
                param.getName());
        return new Diagnostic.Warning(message, unit.positionOf(param));
    }

    private static final class MethodCollector extends TreeScanner<Void, Void> {

        private final List<MethodTree> methods = new ArrayList<>();

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
}
