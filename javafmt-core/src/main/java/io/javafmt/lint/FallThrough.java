package io.javafmt.lint;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.Tree;
import io.javafmt.Diagnostic;
import io.javafmt.parser.ChainScanner;
import io.javafmt.parser.ParsedUnit;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Warns when a colon-form switch case (e.g. {@code case 1:}) silently falls through into the
 * next case. Almost always a bug. Authors must either insert {@code break;} or annotate the
 * boundary with a {@code // fallthrough}-style comment to suppress the warning.
 *
 * <p>Out of scope (v1): arrow-form ({@code case L -> body}) where fall-through is impossible
 * by grammar; multi-path control-flow analysis (e.g. {@code if/else} both exiting); cases
 * whose structure exceeds the shared {@link StatementFlow} helper's one-level block recursion.
 */
public final class FallThrough implements WarningRule {

    private static final Pattern FALL_THROUGH_PATTERN = Pattern.compile("fall.?through", Pattern.CASE_INSENSITIVE);

    private static final String MESSAGE = "case falls through to next case; add 'break;' or a // fallthrough comment if intentional";

    @Override
    public List<Diagnostic> warnings(final LintContext context) {
        final var unit = context.unit();
        return LintScan.collect(context, diagnostics -> new ChainScanner<>() {
            @Override
            protected void visitCases(final Tree node, final List<? extends CaseTree> cases, final Void p) {
                inspect(cases, diagnostics, unit);
            }
        });
    }

    private static void inspect(final List<? extends CaseTree> cases, final List<Diagnostic> diagnostics, final ParsedUnit unit) {
        IntStream.range(0, cases.size() - 1)
                .filter(i -> fallsThrough(cases.get(i)))
                .filter(i -> !hasFallThroughComment(cases.get(i), cases.get(i + 1), unit))
                .forEach(i -> diagnostics.add(new Diagnostic.Warning(MESSAGE, unit.positionOf(cases.get(i)))));
    }

    private static boolean fallsThrough(final CaseTree current) {
        return current.getCaseKind() == CaseTree.CaseKind.STATEMENT
            && !current.getStatements().isEmpty()
            && !StatementFlow.terminates(current.getStatements().getLast());
    }

    private static boolean hasFallThroughComment(final CaseTree current, final CaseTree next, final ParsedUnit unit) {
        final var end = unit.endOf(current);
        final var start = unit.startOf(next);
        if (end < 0 || start < 0 || end > start || start > unit.source().length()) {
            return false;
        }
        return FALL_THROUGH_PATTERN.matcher(unit.source().substring(end, start)).find();
    }
}
