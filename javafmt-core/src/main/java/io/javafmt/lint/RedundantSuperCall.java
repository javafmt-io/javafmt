package io.javafmt.lint;

import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.Optional;

/**
 * Removes a no-arg {@code super()} as the first statement of a constructor body.
 *
 * <p>Java implicitly calls the no-arg superclass constructor when a constructor does not start
 * with an explicit {@code this(...)} or {@code super(...)}. An explicit no-arg {@code super()}
 * is therefore always redundant and should be removed.
 *
 * <p>This also cleans up {@code super()} injected into record compact constructors by tools
 * (including, historically, javafmt's own formatter when run in attributed mode), where the
 * call is not merely redundant but a compile error.
 *
 * <p>Only a no-arg call at the first statement is removed. Calls with arguments ({@code super(x)})
 * and {@code this(...)} delegations are left untouched.
 */
public final class RedundantSuperCall implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

                    @Override
                    public Void visitMethod(final MethodTree node, final Void p) {
                        tryRemove(node, unit).ifPresent(edits::add);
                        return super.visitMethod(node, null);
                    }
                });
    }

    private static Optional<LintEdit> tryRemove(final MethodTree node, final ParsedUnit unit) {
        if (!node.getName().contentEquals("<init>")
            || node.getBody() == null
            || node.getBody().getStatements().isEmpty()) {
            return Optional.empty();
        }
        final var first = node.getBody().getStatements().getFirst();
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var src = unit.source();
        final var start = (int) positions.getStartPosition(root, first);
        final var end = (int) positions.getEndPosition(root, first);
        if (start < 0 || end < 0 || end > src.length()) {
            return Optional.empty();
        }
        if (!src.substring(start, end).strip().equals("super();")) {
            return Optional.empty();
        }
        return Optional.of(deleteEdit(src, start, end));
    }

    /**
     * Deletes the redundant {@code super();} call: the whole line when it owns the line to
     * itself — the formatter's whitespace normalization then collapses any resulting blank line
     * (empty bodies to {@code {}}, indented voids re-trimmed) — or just the statement span when it
     * shares a line with other tokens, so as not to delete a neighbour.
     *
     * <p>A comment on that line counts as a neighbour. {@code super(); // keep this} used to lose
     * the comment with the line, and nothing could catch it: the output verifier compares against
     * the post-lint source, where the comment is already gone.
     */
    private static LintEdit deleteEdit(final String src, final int start, final int end) {
        final var lineStart = RemoveUnusedImports.lineStartFor(src, start);
        final var lineEnd = RemoveUnusedImports.lineEndFor(src, end);
        if (sharesLineWithOtherCode(src, lineStart, start) || lineCarriesAComment(src, lineStart, lineEnd)) {
            return new LintEdit(start, end, "");
        }
        return new LintEdit(lineStart, lineEnd, "");
    }

    private static boolean lineCarriesAComment(final String src, final int lineStart, final int lineEnd) {
        return !CommentScanner.scan(src.substring(lineStart, Math.min(lineEnd, src.length()))).isEmpty();
    }

    private static boolean sharesLineWithOtherCode(final String src, final int lineStart, final int start) {
        return !src.substring(lineStart, start).isBlank();
    }
}
