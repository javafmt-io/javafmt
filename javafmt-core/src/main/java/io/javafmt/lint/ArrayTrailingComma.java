package io.javafmt.lint;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.NewArrayTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.Optional;

/**
 * Adds a trailing comma to every non-empty array initializer, so that subsequent edits
 * adding or reordering elements produce one-line diffs instead of two-line diffs.
 *
 * <p>Strict mode: matches Checkstyle's {@code ArrayTrailingCommaCheck} with
 * {@code alwaysDemandTrailingComma=true}. Empty initializers ({@code {}}) are skipped, as
 * are array creations without an initializer ({@code new int[5]}).
 *
 * <p>Arrays inside annotation arguments are left alone. They are almost always written on one line —
 * {@code @Target({TYPE, METHOD})} — where a trailing comma reads as a typo rather than as diff
 * hygiene, and annotations render from source, so the comma would survive into the output instead
 * of being normalized away.
 *
 * <p>Comma detection looks at the source between the last initializer's end position and the
 * array's end position, with comments stripped out first. Reading the raw text was simpler and the
 * comma inside a comment it mistook for the array's own was written off as too strange to meet;
 * {@code java/lang/CharacterDataLatin1.java} annotates its last element with
 * {@code // 255 , hasLower (add 120), hasTitle, …} and javafmt refused the file, because the rule
 * left the source without a comma and the renderer printed one.
 */
public final class ArrayTrailingComma implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new ChainScanner<>() {

                    @Override
                    public Void visitAnnotation(final AnnotationTree node, final Void p) {
                        return null;
                    }

                    @Override
                    public Void visitNewArray(final NewArrayTree node, final Void p) {
                        editFor(node, unit).ifPresent(edits::add);
                        return super.visitNewArray(node, null);
                    }
                });
    }

    private static Optional<LintEdit> editFor(final NewArrayTree node, final ParsedUnit unit) {
        final var initializers = node.getInitializers();
        if (initializers == null || initializers.isEmpty()) {
            return Optional.empty();
        }
        final var lastElement = initializers.getLast();
        final var lastEnd = unit.endOf(lastElement);
        final var arrayEnd = unit.endOf(node);
        if (lastEnd < 0 || arrayEnd < 0 || lastEnd > arrayEnd) {
            return Optional.empty();
        }
        final var between = CommentScanner.withoutComments(unit.source().substring(lastEnd, arrayEnd));
        if (between.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return Optional.of(LintEdit.insert(lastEnd, ","));
    }
}
