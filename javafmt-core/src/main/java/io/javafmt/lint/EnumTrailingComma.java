package io.javafmt.lint;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.Optional;

/**
 * Adds the trailing comma after the last constant of an enum whose constant list is too wide to
 * print on one line, so that adding or reordering a constant is a one-line diff.
 *
 * <p>This is a lint rule rather than a rendering decision because the output verifier compares the
 * printed text against the <em>post-lint</em> source. A comma the renderer invented would be a
 * token that appears in the output and not in the source, which the verifier reports as corruption
 * — javafmt refused every wide enum that had no trailing comma. Writing the comma into the source
 * puts it on both sides of that comparison.
 *
 * <p>An enum that fits on one line gets nothing: {@code enum Small { A, B }} reads better than
 * {@code enum Small { A, B, }}, and the diff argument does not apply to a list that is not one item
 * per line. A comma or semicolon the author already wrote is left exactly where it is, and an enum
 * with body declarations already needs its {@code ;} so there is nothing to add.
 *
 * <p>The one thing this rule takes away is a {@code ,} written immediately before that {@code ;}.
 * {@code enum E { A, B,; … }} is legal and both marks are redundant, but the renderer emits a
 * single separator, so the pair has to go from the source or the output is a token short.
 */
public final class EnumTrailingComma implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
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
        final var constants = node.getMembers()
                .stream()
                .filter(m -> isConstant(unit, m))
                .map(VariableTree.class::cast)
                .toList();
        if (constants.isEmpty()) {
            return Optional.empty();
        }
        final var lastEnd = unit.endOf(constants.getLast());
        final var enumEnd = unit.endOf(node);
        if (lastEnd < 0 || enumEnd < lastEnd) {
            return Optional.empty();
        }
        final var separators = CommentScanner.withoutComments(unit.source().substring(lastEnd, enumEnd));
        final var semicolon = separators.indexOf(';');
        if (semicolon >= 0) {
            return redundantCommaBeforeSemicolon(separators, semicolon, lastEnd);
        }
        if (separators.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return fitsOnOneLine(node, constants, unit) ? Optional.empty() : Optional.of(LintEdit.insert(lastEnd, ","));
    }

    /**
     * Deletes a {@code ,} the author left in front of the {@code ;} that ends the constant list.
     * Both are legal and both are redundant, but the renderer emits one separator, so leaving the
     * pair in the source means the output is a token short and the file comes back refused.
     *
     * <p>Comments have already been stripped from {@code separators}, so an offset in it is not an
     * offset in the file; the comma is located again in the source itself.
     */
    private static Optional<LintEdit> redundantCommaBeforeSemicolon(final String separators, final int semicolon, final int lastEnd) {
        if (separators.lastIndexOf(',', semicolon) < 0) {
            return Optional.empty();
        }
        return Optional.of(new LintEdit(lastEnd, lastEnd + 1, ""));
    }

    /**
     * Whether the renderer will keep the whole enum on one line, which it prints as
     * {@code <header> { A, B }}. Measured from the source rather than rebuilt from the tree so the
     * header — modifiers, name, an {@code implements} clause — does not have to be reconstructed
     * here; the flattening collapses the author's whitespace to the single spaces the renderer uses.
     */
    private static boolean fitsOnOneLine(final ClassTree node, final List<VariableTree> constants, final ParsedUnit unit) {
        final var enumStart = unit.startOf(node);
        final var firstConstantStart = unit.startOf(constants.getFirst());
        if (enumStart < 0 || firstConstantStart <= enumStart) {
            return false;
        }
        final var header = SourceLayout.flatten(unit.source().substring(enumStart, firstConstantStart));
        final var body = constants.stream()
                .map(c -> SourceLayout.flatten(unit.source().substring(unit.startOf(c), unit.endOf(c))))
                .toList();
        final var oneLine = header + " " + String.join(", ", body) + " }";
        return SourceLayout.fits(SourceLayout.columnOf(unit.source(), enumStart), oneLine.length());
    }

    /**
     * An enum constant has no type written in the source, so javac gives its synthesised type tree
     * no position. Same test {@code TreeFacts.isEnumConstant} uses.
     */
    private static boolean isConstant(final ParsedUnit unit, final Tree member) {
        return member instanceof VariableTree v
            && v.getType() != null
            && unit.endOf(v.getType()) < 0;
    }
}
