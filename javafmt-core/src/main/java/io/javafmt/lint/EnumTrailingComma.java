package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.Optional;

/**
 * Adds the trailing comma after the last constant of an enum, so that adding or reordering a
 * constant is a one-line diff. The renderer prints it only when the constants go one per line.
 *
 * <p>A comma or semicolon the author already wrote is left exactly where it is, and an enum
 * with body declarations already needs its {@code ;} so there is nothing to add.
 *
 * <p>The one thing this rule takes away is a {@code ,} written immediately before that {@code ;}.
 * {@code enum E { A, B,; … }} is legal and both marks are redundant, but the renderer emits a
 * single separator, so the pair has to go from the source or the output is a token short.
 */
public final class EnumTrailingComma implements LintRule {

    @Override
    public LintResult apply(final LintContext context) {
        final var unit = context.unit();
        return LintScan.edits(
                context,
                edits -> new ChainScanner<>() {
                    @Override
                    public Void visitClass(final ClassTree node, final Void p) {
                        if (node.getKind() == Tree.Kind.ENUM) {
                            editFor(node, unit).ifPresent(edits::add);
                        }
                        return super.visitClass(node, null);
                    }
                });
    }

    private static Optional<LintEdit> editFor(final ClassTree node, final ParsedUnit unit) {
        final var constants = node.getMembers().stream().filter(m -> isConstant(unit, m)).map(VariableTree.class::cast).toList();
        if (constants.isEmpty()) {
            return Optional.empty();
        }
        final var lastEnd = unit.endOf(constants.getLast());
        final var enumEnd = unit.endOf(node);
        if (lastEnd < 0 || enumEnd < lastEnd) {
            return Optional.empty();
        }
        final var separators = CommentScanner.withoutComments(unit.source().substring(lastEnd, enumEnd));
        if (separators.indexOf(';') >= 0) {
            return redundantCommaBeforeSemicolon(unit.source(), lastEnd);
        }
        if (separators.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return Optional.of(TrailingCommas.insertAfter(unit.source(), lastEnd));
    }

    /**
     * Deletes a {@code ,} the author left in front of the {@code ;} that ends the constant list.
     * Both are legal and both are redundant, but the renderer emits one separator, so leaving the
     * pair in the source means the output is a token short and the file comes back refused.
     */
    private static Optional<LintEdit> redundantCommaBeforeSemicolon(final String source, final int from) {
        final var commentEnd = CommentScanner.skipComment(source, from);
        if (commentEnd >= 0) {
            return redundantCommaBeforeSemicolon(source, commentEnd);
        }
        return switch (source.charAt(from)) {
            case ',' -> Optional.of(new LintEdit(from, from + 1, ""));
            case ';' -> Optional.empty();
            default -> redundantCommaBeforeSemicolon(source, from + 1);
        };
    }

    /**
     * An enum constant has no type written in the source, so javac gives its synthesised type tree
     * no position. Same test {@code TreeFacts.isEnumConstant} uses.
     */
    private static boolean isConstant(final ParsedUnit unit, final Tree member) {
        return member instanceof VariableTree v && v.getType() != null && unit.endOf(v.getType()) < 0;
    }
}
