package io.javafmt.lint;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import io.javafmt.Diagnostic;
import io.javafmt.parser.ParsedUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves a misplaced {@code default} case to the end of its switch when safe; otherwise
 * warns. Default-case-not-last forces readers to chase fall-through (or scan the whole
 * switch) before they know what runs by default.
 *
 * <p>Auto-fix applies when the move can't change behavior:
 * <ul>
 *   <li>Arrow-form switches — fall-through is impossible by grammar, so reordering is
 *       always safe.</li>
 *   <li>Colon-form switches when all four hold: default is the sole label on its case;
 *       default's body is non-empty; default's body terminates control flow; and the case
 *       immediately preceding default has a non-empty terminating body. The first three
 *       guarantee default is structurally independent and movable as a unit; the fourth
 *       rules out unreported fall-through and label grouping.</li>
 * </ul>
 *
 * <p>Otherwise the rule emits a {@link Diagnostic.Warning} and leaves the source alone.
 */
public final class DefaultComesLast implements LintRule {

    private static final String UNSAFE_MESSAGE =
        "default case should be last in switch (auto-fix skipped: shared label or fall-through grouping)";

    @Override
    public String name() {
        return "DefaultComesLast";
    }

    @Override
    public LintResult apply(final ParsedUnit unit) {
        final var edits = new ArrayList<LintEdit>();
        final var diagnostics = new ArrayList<Diagnostic>();
        final var scanner = new TreePathScanner<Void, Void>() {

            @Override
            public Void visitSwitch(final SwitchTree node, final Void p) {
                inspect(node, node.getCases(), edits, diagnostics, unit);
                return super.visitSwitch(node, null);
            }

            @Override
            public Void visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
                inspect(node, node.getCases(), edits, diagnostics, unit);
                return super.visitSwitchExpression(node, null);
            }
        };
        scanner.scan(new TreePath(unit.tree()), null);
        return new LintResult(edits, diagnostics);
    }

    private static void inspect(
            final Tree switchNode,
            final List<? extends CaseTree> cases,
            final List<LintEdit> edits,
            final List<Diagnostic> diagnostics,
            final ParsedUnit unit) {
        final var defaultIndex = indexOfDefault(cases);
        if (defaultIndex < 0 || defaultIndex == cases.size() - 1) {
            return;
        }
        final var defaultCase = cases.get(defaultIndex);
        if (canAutoFix(defaultCase, defaultIndex, cases)) {
            edits.addAll(buildMoveEdits(switchNode, defaultCase, unit));
        } else {
            diagnostics.add(new Diagnostic.Warning(UNSAFE_MESSAGE, unit.positionOf(defaultCase)));
        }
    }

    private static int indexOfDefault(final List<? extends CaseTree> cases) {
        for (var i = 0; i < cases.size(); i++) {
            final var labels = cases.get(i).getLabels();
            if (labels.stream().anyMatch(l -> l instanceof DefaultCaseLabelTree)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean canAutoFix(
            final CaseTree defaultCase, final int defaultIndex, final List<? extends CaseTree> cases) {
        if (defaultCase.getCaseKind() == CaseTree.CaseKind.RULE) {
            return true;
        }
        if (defaultCase.getLabels().size() != 1) {
            return false;
        }
        final var defaultStmts = defaultCase.getStatements();
        if (defaultStmts.isEmpty()) {
            return false;
        }
        if (!StatementFlow.terminates(defaultStmts.get(defaultStmts.size() - 1))) {
            return false;
        }
        if (defaultIndex == 0) {
            return true;
        }
        final var predecessor = cases.get(defaultIndex - 1);
        final var predStmts = predecessor.getStatements();
        if (predStmts.isEmpty()) {
            return false;
        }
        return StatementFlow.terminates(predStmts.get(predStmts.size() - 1));
    }

    private static List<LintEdit> buildMoveEdits(
            final Tree switchNode, final CaseTree defaultCase, final ParsedUnit unit) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var defaultStart = (int) positions.getStartPosition(root, defaultCase);
        final var defaultEnd = (int) positions.getEndPosition(root, defaultCase);
        final var switchEnd = (int) positions.getEndPosition(root, switchNode);
        if (defaultStart < 0 || defaultEnd < 0 || switchEnd < 0
                || defaultEnd > switchEnd || switchEnd > unit.source().length()) {
            return List.of();
        }
        final var defaultText = unit.source().substring(defaultStart, defaultEnd);
        return List.of(
            new LintEdit(defaultStart, defaultEnd, ""),
            LintEdit.insert(switchEnd - 1, defaultText));
    }
}
