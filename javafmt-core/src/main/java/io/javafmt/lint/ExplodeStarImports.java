package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.Attribution;
import io.javafmt.parser.ParsedUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

/**
 * Replaces wildcard imports with explicit imports for the members actually referenced.
 *
 * <p>Requires attribution, so the rule only fires under {@code --analyze}. Membership is read off
 * the symbol each identifier resolves to: the declaration's owner is compared against the
 * wildcard's prefix. Nothing is guessed, so the result cannot vary with how javafmt happens to be
 * packaged, and a name the file's own package supplies is never rebound to a same-named type from
 * the wildcard's package.
 *
 * <p>If any identifier in the file fails to resolve, every wildcard is left alone. An unresolved
 * name may be one the wildcard supplies, and replacing the wildcard while missing one of its
 * members would not compile. A wildcard that supplies nothing referenced is also left alone rather
 * than deleted — {@link AvoidStarImport} reports it, and deleting an import this rule cannot
 * account for is exactly the unsafe direction.
 */
public final class ExplodeStarImports implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var attribution = ctx.attribution();
        if (attribution.isEmpty()) {
            return LintResult.EMPTY;
        }
        final var unit = ctx.unit();
        final var stars = unit.tree()
                .getImports()
                .stream()
                .filter(ExplodeStarImports::isStarImport)
                .toList();
        if (stars.isEmpty()) {
            return LintResult.EMPTY;
        }
        return resolveUses(unit, attribution.get())
                .map(uses -> LintResult.ofEdits(stars.stream().flatMap(s -> editFor(s, uses, attribution.get(), unit).stream()).toList()))
                .orElse(LintResult.EMPTY);
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals("*");
    }

    private static String prefixOf(final ImportTree imp) {
        return ((MemberSelectTree) imp.getQualifiedIdentifier()).getExpression().toString();
    }

    /**
     * The declaration behind every simple name in the file, or empty if any of them failed to
     * resolve. Comment text is not scanned: a wildcard kept alive only by a Javadoc reference will
     * be exploded without that name, which costs a {@code -Xdoclint} warning rather than a build.
     */
    private static Optional<List<Element>> resolveUses(final ParsedUnit unit, final Attribution attribution) {
        final var identifierPaths = new ArrayList<TreePath>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitImport(final ImportTree node, final Void p) {
                return null;
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void p) {
                identifierPaths.add(getCurrentPath());
                return null;
            }
        }.scan(new TreePath(unit.tree()), null);
        final var elements = identifierPaths.stream()
                .map(path -> attribution.trees().getElement(path))
                .toList();
        final var anyUnresolved = elements.stream().anyMatch(e -> e == null || e.asType().getKind() == TypeKind.ERROR);
        return anyUnresolved ? Optional.empty() : Optional.of(elements);
    }

    private static Optional<LintEdit> editFor(final ImportTree imp, final List<Element> uses, final Attribution attribution, final ParsedUnit unit) {
        final var prefix = prefixOf(imp);
        final var names = uses.stream()
                .filter(use -> isSuppliedBy(use, prefix, imp.isStatic(), attribution))
                .map(use -> use.getSimpleName().toString())
                .collect(Collectors.toCollection(TreeSet::new));
        if (names.isEmpty()) {
            return Optional.empty();
        }
        return RemoveUnusedImports.spanOf(imp, unit)
                .map(span -> new LintEdit(span.start(), span.end(), replacementFor(imp, prefix, names, span, unit)));
    }

    /**
     * Whether this wildcard is what brought the name into scope. For {@code pkg.*} — and for
     * {@code Outer.*} naming nested types — the declaration's owner must be the prefix itself. A
     * static import-on-demand additionally covers members the prefix class inherits, whose owner
     * is a supertype.
     */
    private static boolean isSuppliedBy(final Element use, final String prefix, final boolean isStatic, final Attribution attribution) {
        if (!isImportable(use, isStatic)) {
            return false;
        }
        final var owner = use.getEnclosingElement();
        if (owner instanceof QualifiedNameable named && named.getQualifiedName().contentEquals(prefix)) {
            return true;
        }
        return isStatic
            && owner instanceof TypeElement ownerType
            && prefixInheritsFrom(ownerType, prefix, attribution);
    }

    /**
     * Whether an import declaration could name this element at all. A type-import-on-demand names
     * only types; a static-import-on-demand names only static members. Filtering here also drops
     * javac's synthesized {@code super()} call, which resolves to a constructor named
     * {@code <init>} owned by a supertype of every possible prefix.
     */
    private static boolean isImportable(final Element use, final boolean isStatic) {
        if (!isStatic) {
            return use instanceof TypeElement;
        }
        return switch (use.getKind()) {
            case FIELD, ENUM_CONSTANT, METHOD, CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> use.getModifiers().contains(Modifier.STATIC);
            default -> false;
        };
    }

    private static boolean prefixInheritsFrom(final TypeElement owner, final String prefix, final Attribution attribution) {
        final var prefixType = attribution.elements().getTypeElement(prefix);
        if (prefixType == null) {
            return false;
        }
        final var types = attribution.types();
        return types.isSubtype(types.erasure(prefixType.asType()), types.erasure(owner.asType()));
    }

    /**
     * The explicit imports replacing the wildcard, one per line. The whole-line span swallows the
     * newline that terminated the wildcard, so the replacement must restore it; a span shared with
     * another declaration on the same line does not end in a newline, so it must not either.
     */
    private static String replacementFor(
            final ImportTree imp,
            final String prefix,
            final Set<String> names,
            final RemoveUnusedImports.Span span,
            final ParsedUnit unit) {
        final var keyword = imp.isStatic() ? "import static " : "import ";
        final var body = names.stream()
                .map(name -> keyword + prefix + "." + name + ";")
                .collect(Collectors.joining("\n"));
        final var replacedTrailingNewline = span.end() > 0 && unit.source().charAt(span.end() - 1) == '\n';
        return replacedTrailingNewline ? body + "\n" : body;
    }
}
