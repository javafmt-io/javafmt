package io.javafmt.lint;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deletes single-name imports whose simple name is not referenced as an identifier anywhere
 * else in the compilation unit.
 *
 * <p>"Referenced" is decided by collecting every {@link IdentifierTree} name in the parsed
 * tree, ignoring nodes inside {@link ImportTree}s themselves. Since javafmt has no type
 * resolution at lint time, the rule is intentionally conservative: a local variable that
 * shadows an imported name will keep the import alive. That's a false-negative (imports we
 * could remove but don't), never a false-positive (imports we incorrectly delete and break
 * compilation), so it satisfies the safe-fix contract.
 *
 * <p>Star imports are out of scope — this rule has no way to know which members of a
 * wildcard package are referenced. {@link ExplodeStarImports} handles those instead.
 *
 * <p>Identifiers in Javadoc are not part of the parsed AST, so a name referenced only from
 * {@code @link} or {@code @see} would look unused. Comment text is therefore scanned for
 * identifier-shaped words and those count as references, matching google-java-format: removing
 * such an import compiles but breaks any build running {@code -Xdoclint}. The oracle test uses
 * {@code processJavadoc=true} on Checkstyle's equivalent check to match this scope.
 */
public final class RemoveUnusedImports implements LintRule {

    private static final Pattern IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var imports = unit.tree().getImports();
        if (imports.isEmpty()) {
            return LintResult.EMPTY;
        }
        final var usedNames = collectUsedNames(unit);
        return LintResult.ofEdits(
                imports.stream()
                        .filter(i -> !i.isModule())
                        .filter(i -> !isStarImport(i))
                        .filter(i -> !usedNames.contains(simpleNameOf(i)))
                        .flatMap(i -> deleteEdit(i, unit).stream())
                        .toList());
    }

    private static boolean isStarImport(final ImportTree imp) {
        return imp.getQualifiedIdentifier() instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals("*");
    }

    private static String simpleNameOf(final ImportTree imp) {
        final var qualified = imp.getQualifiedIdentifier();
        return qualified instanceof MemberSelectTree ms ? ms.getIdentifier().toString() : ((IdentifierTree) qualified).getName().toString();
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
        names.addAll(namesMentionedInComments(unit.source()));
        return Set.copyOf(names);
    }

    /**
     * Every identifier-shaped word appearing in a comment. Whole-word matching keeps
     * {@code Lists} from reading as a reference to {@code List}, and taking the words from
     * comments rather than parsing Javadoc tags means {@code @see}, {@code @throws} and prose
     * mentions all count — the point is only ever to keep an import, never to remove one.
     */
    private static Set<String> namesMentionedInComments(final String source) {
        return CommentScanner.scan(source)
                .stream()
                .flatMap(comment -> IDENTIFIER.matcher(comment.text()).results())
                .map(MatchResult::group)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<LintEdit> deleteEdit(final ImportTree imp, final ParsedUnit unit) {
        return spanOf(imp, unit).map(span -> new LintEdit(span.start(), span.end(), ""));
    }

    /**
     * The range to remove or replace for a single import. When the declaration is alone on its line
     * the range covers the whole line, so removing it leaves no blank line behind. When it shares a
     * line with another declaration the range stops at its own semicolon — deleting the line would
     * take the neighbouring import with it.
     */
    static Optional<Span> spanOf(final ImportTree imp, final ParsedUnit unit) {
        final var source = unit.source();
        final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), imp);
        if (start < 0) {
            return Optional.empty();
        }
        final var semicolon = source.indexOf(';', start);
        if (semicolon < 0) {
            return Optional.empty();
        }
        final var declarationEnd = semicolon + 1;
        final var lineStart = lineStartFor(source, start);
        final var lineEnd = lineEndFor(source, declarationEnd);
        final var aloneOnLine = source.substring(lineStart, start).isBlank() && source.substring(declarationEnd, lineEnd).isBlank();
        return Optional.of(aloneOnLine ? new Span(lineStart, lineEnd) : new Span(start, declarationEnd));
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

    /** The source range one import declaration owns, as {@link #spanOf} decides it. */
    record Span(int start, int end) {}
}
