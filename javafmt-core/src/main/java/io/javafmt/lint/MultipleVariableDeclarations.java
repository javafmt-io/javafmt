package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Splits {@code int a, b, c = 0;} into separate declarations. Detects groups by checking
 * whether consecutive {@link VariableTree} siblings in the same block or class share the
 * same type start offset, which javac produces for multi-variable declarations.
 */
public final class MultipleVariableDeclarations implements LintRule {

    @Override
    public String name() {
        return "MultipleVariableDeclarations";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitBlock(final BlockTree node, final Void p) {
                splitGroups(node.getStatements(), unit, edits);
                return super.visitBlock(node, null);
            }

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                splitGroups(node.getMembers(), unit, edits);
                return super.visitClass(node, null);
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }

    private static void splitGroups(
            final List<? extends Tree> stmts,
            final ParsedUnit unit,
            final List<LintEdit> edits) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        var i = 0;
        while (i < stmts.size()) {
            if (!(stmts.get(i) instanceof VariableTree firstVar)) {
                i++;
                continue;
            }
            final var typeStart = (int) positions.getStartPosition(root, firstVar.getType());
            var j = i + 1;
            while (j < stmts.size()
                    && stmts.get(j) instanceof VariableTree nextVar
                    && (int) positions.getStartPosition(root, nextVar.getType()) == typeStart) {
                j++;
            }
            if (j - i >= 2) {
                final var group = stmts.subList(i, j).stream()
                    .map(t -> (VariableTree) t)
                    .toList();
                final var edit = buildEdit(group, unit);
                if (edit != null) {
                    edits.add(edit);
                }
            }
            i = j;
        }
    }

    private static @Nullable LintEdit buildEdit(
            final List<VariableTree> group, final ParsedUnit unit) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var first = group.get(0);
        final var last = group.get(group.size() - 1);
        final var groupStart = (int) positions.getStartPosition(root, first);
        final var lastEnd = (int) positions.getEndPosition(root, last);
        if (groupStart < 0 || lastEnd < 0) {
            return null;
        }
        final var src = unit.source();
        // javac's getEndPosition for the last variable in a multi-decl is 1 past the ';'
        var semiPos = lastEnd - 1;
        while (semiPos < src.length() && src.charAt(semiPos) != ';') {
            semiPos++;
        }
        if (semiPos >= src.length()) {
            return null;
        }
        final var spanEnd = semiPos + 1;
        final var indent = extractIndent(src, groupStart);
        final var firstTypeEnd = (int) positions.getEndPosition(root, first.getType());
        if (firstTypeEnd < 0) {
            return null;
        }
        // modifiers + type portion (from declaration start to end of type keyword)
        final var modAndType = src.substring(groupStart, firstTypeEnd);

        final var sb = new StringBuilder();
        for (var k = 0; k < group.size(); k++) {
            if (k > 0) {
                sb.append("\n").append(indent);
            }
            final var v = group.get(k);
            sb.append(modAndType).append(" ").append(v.getName());
            if (v.getInitializer() != null) {
                sb.append(" = ").append(unit.sourceOf(v.getInitializer()));
            }
            sb.append(";");
        }
        return new LintEdit(groupStart, spanEnd, sb.toString());
    }

    static String extractIndent(final String src, final int pos) {
        var lineStart = pos;
        while (lineStart > 0 && src.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        var end = lineStart;
        while (end < pos && (src.charAt(end) == ' ' || src.charAt(end) == '\t')) {
            end++;
        }
        return src.substring(lineStart, end);
    }
}
