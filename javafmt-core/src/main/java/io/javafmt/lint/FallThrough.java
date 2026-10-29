package io.javafmt.lint;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainPathScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Warns when a colon-form switch case (e.g. {@code case 1:}) silently falls through into the
 * next case. Almost always a bug. Authors must either insert {@code break;} or annotate the
 * boundary with a {@code // fallthrough}-style comment to suppress the warning.
 *
 * <p>Out of scope (v1): arrow-form ({@code case L -> body}) where fall-through is impossible
 * by grammar; multi-path control-flow analysis (e.g. {@code if/else} both exiting); cases
 * whose structure exceeds the shared {@link StatementFlow} helper's one-level block recursion.
 */
public final class FallThrough implements LintRule {

    private static final Pattern FALL_THROUGH_PATTERN = Pattern.compile("fall.?through", Pattern.CASE_INSENSITIVE);

    private static final String MESSAGE = "case falls through to next case; add 'break;' or a // fallthrough comment if intentional";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.diagnostics(
                ctx,
                diagnostics -> new ChainPathScanner<>() {

                    @Override
                    public Void visitSwitch(final SwitchTree node, final Void p) {
                        inspect(node.getCases(), diagnostics, unit);
                        return super.visitSwitch(node, null);
                    }

                    @Override
                    public Void visitSwitchExpression(final SwitchExpressionTree node, final Void p) {
                        inspect(node.getCases(), diagnostics, unit);
                        return super.visitSwitchExpression(node, null);
                    }
                });
    }

    private static void inspect(final List<? extends CaseTree> cases, final List<Diagnostic> diagnostics, final ParsedUnit unit) {
        for (var i = 0; i < cases.size() - 1; i++) {
            final var current = cases.get(i);
            if (current.getCaseKind() != CaseTree.CaseKind.STATEMENT) {
                continue;
            }
            final var stmts = current.getStatements();
            if (stmts.isEmpty()) {
                continue;
            }
            final var last = stmts.getLast();
            if (StatementFlow.terminates(last)) {
                continue;
            }
            if (hasFallThroughComment(current, cases.get(i + 1), unit)) {
                continue;
            }
            diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(current)));
        }
    }

    private static boolean hasFallThroughComment(final CaseTree current, final CaseTree next, final ParsedUnit unit) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var end = (int) positions.getEndPosition(root, current);
        final var start = (int) positions.getStartPosition(root, next);
        if (end < 0 || start < 0 || end > start || start > unit.source().length()) {
            return false;
        }
        return FALL_THROUGH_PATTERN.matcher(unit.source().substring(end, start)).find();
    }
}
