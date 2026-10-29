package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import io.javafmt.Diagnostic;
import java.util.List;
import java.util.Optional;

/**
 * Warns if a class overrides {@code equals(Object)} without overriding {@code hashCode()},
 * or vice versa. The contract of {@link Object#equals} requires a consistent
 * {@link Object#hashCode}.
 */
public final class EqualsHashCode implements WarningRule {

    @Override
    public List<Diagnostic> warnings(final LintContext ctx) {
        return WarningRule.onNode(ctx, ClassTree.class, EqualsHashCode::warningFor);
    }

    private static Optional<String> warningFor(final ClassTree node) {
        final var methods = node.getMembers()
                .stream()
                .filter(m -> m instanceof MethodTree)
                .map(m -> (MethodTree) m)
                .toList();
        final var hasEquals = methods.stream().anyMatch(Declarations::isEqualsObject);
        final var hasHashCode = methods.stream().anyMatch(EqualsHashCode::isHashCode);
        if (hasEquals && !hasHashCode) {
            return Optional.of("class overrides equals(Object) but not hashCode()");
        }
        if (!hasEquals && hasHashCode) {
            return Optional.of("class overrides hashCode() but not equals(Object)");
        }
        return Optional.empty();
    }

    private static boolean isHashCode(final MethodTree method) {
        return method.getName().contentEquals("hashCode") && method.getParameters().isEmpty();
    }
}
