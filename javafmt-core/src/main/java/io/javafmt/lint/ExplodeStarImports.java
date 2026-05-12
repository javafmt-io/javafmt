package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replaces wildcard imports with explicit imports for the members actually referenced.
 *
 * <p>Without type resolution at lint time, membership is decided by reflection against the
 * runtime classloader: for {@code import pkg.*;} we try {@code Class.forName(pkg.Name)}
 * for every identifier name in the file; for {@code import static Cls.*;} we enumerate
 * {@code Cls}'s public static members and intersect with file identifiers. This works for
 * anything reachable on the formatter's classpath — which covers the JDK and any deps
 * javafmt itself uses, but not arbitrary user-code wildcards into project-local packages.
 * That limitation is acceptable for v1; star imports of project-local packages are rare
 * and the rule simply leaves them untouched (zero resolved members -> import deleted, which
 * may break compilation, so we keep the import as-is in that case).
 *
 * <p>{@code Class.forName(name, false, loader)} skips static initialization so resolving a
 * candidate name doesn't run user-class side effects.
 */
public final class ExplodeStarImports implements LintRule {

    @Override
    public String name() {
        return "ExplodeStarImports";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var imports = unit.tree().getImports();
        if (imports.isEmpty()) {
            return LintResult.EMPTY;
        }
        final var stars = imports.stream().filter(ExplodeStarImports::isStarImport).toList();
        if (stars.isEmpty()) {
            return LintResult.EMPTY;
        }
        final var candidates = collectIdentifierNames(unit);
        return LintResult.ofEdits(stars.stream()
            .flatMap(s -> editFor(s, candidates, unit).stream())
            .toList());
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms
            && ms.getIdentifier().contentEquals("*");
    }

    private static String prefixOf(final ImportTree imp) {
        return ((MemberSelectTree) imp.getQualifiedIdentifier()).getExpression().toString();
    }

    private static Set<String> collectIdentifierNames(final ParsedUnit unit) {
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

    private static Optional<LintEdit> editFor(
            final ImportTree imp, final Set<String> candidates, final ParsedUnit unit) {
        final var source = unit.source();
        final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), imp);
        final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), imp);
        if (start < 0 || end < 0) {
            return Optional.empty();
        }
        final var prefix = prefixOf(imp);
        final var resolved = imp.isStatic()
            ? resolveStaticMembers(prefix, candidates)
            : resolveClasses(prefix, candidates);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        final var importPrefix = imp.isStatic() ? "import static " : "import ";
        final var replacement = resolved.stream()
            .map(name -> importPrefix + prefix + "." + name + ";")
            .collect(Collectors.joining("\n", "", "\n"));
        return Optional.of(new LintEdit(
            RemoveUnusedImports.lineStartFor(source, start),
            RemoveUnusedImports.lineEndFor(source, end),
            replacement));
    }

    private static Set<String> resolveClasses(final String pkg, final Set<String> candidates) {
        return candidates.stream()
            .filter(name -> !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0)))
            .filter(name -> classExists(pkg + "." + name))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> resolveStaticMembers(final String className, final Set<String> candidates) {
        final var cls = loadClass(className);
        if (cls.isEmpty()) {
            return Set.of();
        }
        final var members = staticMemberNames(cls.get());
        return candidates.stream()
            .filter(members::contains)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> staticMemberNames(final Class<?> cls) {
        return Stream.of(
                Arrays.stream(cls.getMethods()).filter(m -> Modifier.isStatic(m.getModifiers())).map(m -> m.getName()),
                Arrays.stream(cls.getFields()).filter(f -> Modifier.isStatic(f.getModifiers())).map(f -> f.getName()),
                Arrays.stream(cls.getClasses()).filter(c -> Modifier.isStatic(c.getModifiers())).map(Class::getSimpleName))
            .flatMap(s -> s)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean classExists(final String fqn) {
        return loadClass(fqn).isPresent();
    }

    private static Optional<Class<?>> loadClass(final String fqn) {
        try {
            return Optional.of(Class.forName(fqn, false, ExplodeStarImports.class.getClassLoader()));
        } catch (final ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }
}
