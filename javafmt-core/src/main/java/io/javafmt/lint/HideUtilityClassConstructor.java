package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.Diagnostic;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Warns if a class appears to be a utility class — a plain class whose whole API is static — but
 * has no private constructor. Without one the class can be accidentally instantiated.
 *
 * <p>Three conditions were derived from {@code HideUtilityClassConstructorCheck} itself, through
 * the oracle in {@code oracleTest}, after javafmt was run over its own source and warned about
 * three things Checkstyle is silent on:
 *
 * <ul>
 *   <li>A class with an {@code extends} clause is never a utility class. It inherits behaviour, so
 *       its instances mean something. {@code LintRewriteException extends RuntimeException} was
 *       flagged for carrying a {@code serialVersionUID} and nothing else static. An
 *       {@code implements} clause does not exclude it — an interface carries no state.</li>
 *   <li>Only a <em>non-private</em> static member counts. The question is whether the class offers
 *       an API reachable without an instance; a private static helper offers nothing, which is why
 *       a test class holding one private helper and a {@code @Nested} class was flagged.</li>
 *   <li>Any private constructor hides the class, not only a no-arg one. Nothing outside can call
 *       either.</li>
 * </ul>
 */
public final class HideUtilityClassConstructor implements LintRule {

    private static final String MESSAGE = "utility class should have a private constructor to prevent instantiation";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new TreePathScanner<>() {

                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        if (isUtilityCandidate(node) && !hasPrivateConstructor(node)) {
                            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(node)));
                        }
                        return super.visitClass(node, null);
                    }
                });
    }

    private static boolean isUtilityCandidate(final ClassTree node) {
        if (node.getKind() != Tree.Kind.CLASS
            || node.getModifiers().getFlags().contains(Modifier.ABSTRACT)
            || node.getExtendsClause() != null) {
            return false;
        }
        var hasStaticApi = false;
        for (var member : node.getMembers()) {
            final var modifiers = modifiersOf(member);
            if (modifiers == null || isConstructor(member)) {
                continue;
            }
            if (!modifiers.getFlags().contains(Modifier.STATIC)) {
                return false;
            }
            hasStaticApi = hasStaticApi || !modifiers.getFlags().contains(Modifier.PRIVATE);
        }
        return hasStaticApi;
    }

    /** The modifiers of a method or field member, or {@code null} for anything else. */
    @Nullable private static ModifiersTree modifiersOf(final Tree member) {
        return switch (member) {
            case MethodTree method -> method.getModifiers();
            case VariableTree field -> field.getModifiers();
            default -> null;
        };
    }

    private static boolean isConstructor(final Tree member) {
        return member instanceof MethodTree method && method.getReturnType() == null;
    }

    private static boolean hasPrivateConstructor(final ClassTree node) {
        return node.getMembers()
                .stream()
                .filter(HideUtilityClassConstructor::isConstructor)
                .map(MethodTree.class::cast)
                .anyMatch(m -> m.getModifiers().getFlags().contains(Modifier.PRIVATE));
    }
}
