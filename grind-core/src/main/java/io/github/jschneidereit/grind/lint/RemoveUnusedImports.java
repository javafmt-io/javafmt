package io.github.jschneidereit.grind.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Deletes single-name imports whose simple name is not referenced as an identifier anywhere
 * else in the compilation unit.
 *
 * <p>"Referenced" is decided by collecting every {@link IdentifierTree} name in the parsed
 * tree, ignoring nodes inside {@link ImportTree}s themselves. Since grind has no type
 * resolution at lint time, the rule is intentionally conservative: a local variable that
 * shadows an imported name will keep the import alive. That's a false-negative (imports we
 * could remove but don't), never a false-positive (imports we incorrectly delete and break
 * compilation), so it satisfies the safe-fix contract.
 *
 * <p>Star imports are out of scope — this rule has no way to know which members of a
 * wildcard package are referenced. {@link ExplodeStarImports} handles those instead.
 *
 * <p>Identifiers in Javadoc are not part of the parsed AST, so {@code @link} references
 * don't keep an import alive. The oracle test uses {@code processJavadoc=false} on
 * Checkstyle's equivalent check to match this scope.
 */
public final class RemoveUnusedImports implements LintRule {

    @Override
    public String name() {
        return "RemoveUnusedImports";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var imports = unit.tree().getImports();
        if (imports.isEmpty()) {
            return LintResult.EMPTY;
        }
        final var usedNames = collectUsedNames(unit);
        return LintResult.ofEdits(imports.stream()
            .filter(i -> !isStarImport(i))
            .filter(i -> !usedNames.contains(simpleNameOf(i)))
            .flatMap(i -> deleteEdit(i, unit).stream())
            .toList());
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms
            && ms.getIdentifier().contentEquals("*");
    }

    private static String simpleNameOf(final ImportTree imp) {
        final var qualified = imp.getQualifiedIdentifier();
        return qualified instanceof MemberSelectTree ms
            ? ms.getIdentifier().toString()
            : ((IdentifierTree) qualified).getName().toString();
    }

    private static Set<String> collectUsedNames(final ParsedUnit unit) {
        final var names = new HashSet<String>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitImport(final ImportTree node, final Void p) {
                return null;
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void p) {
                names.add(node.getName().toString());
                return null;
            }
        }.scan(new TreePath(unit.tree()), null);
        return Set.copyOf(names);
    }

    private static Optional<LintEdit> deleteEdit(final ImportTree imp, final ParsedUnit unit) {
        final var source = unit.source();
        final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), imp);
        final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), imp);
        if (start < 0 || end < 0) {
            return Optional.empty();
        }
        return Optional.of(new LintEdit(lineStartFor(source, start), lineEndFor(source, end), ""));
    }

    static int lineStartFor(final String source, final int pos) {
        var i = pos;
        while (i > 0 && source.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    static int lineEndFor(final String source, final int pos) {
        var i = pos;
        while (i < source.length() && source.charAt(i) != '\n') {
            i++;
        }
        return i < source.length() ? i + 1 : i;
    }
}
