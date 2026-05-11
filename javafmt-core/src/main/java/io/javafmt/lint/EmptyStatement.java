package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;

/**
 * Removes stray semicolons ({@code ;}) that appear as {@link EmptyStatementTree} nodes inside
 * block bodies. Does not touch {@code for(;;)} loops — their empty init/condition/update are
 * not represented as {@link EmptyStatementTree} at all.
 */
public final class EmptyStatement implements LintRule {

    @Override
    public String name() {
        return "EmptyStatement";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitEmptyStatement(final EmptyStatementTree node, final Void p) {
                if (getCurrentPath().getParentPath().getLeaf() instanceof BlockTree) {
                    final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
                    final var end = (int) unit.sourcePositions().getEndPosition(unit.tree(), node);
                    if (start >= 0 && end > start) {
                        edits.add(new LintEdit(start, end, ""));
                    }
                }
                return null;
            }
        }.scan(new TreePath(unit.tree()), null);
        return LintResult.ofEdits(edits);
    }
}
