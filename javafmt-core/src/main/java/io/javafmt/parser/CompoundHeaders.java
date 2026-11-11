package io.javafmt.parser;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SourcePositions;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@UtilityClass
public final class CompoundHeaders {

    private final Pattern EMPTY_PARTS_AND_THE_CLOSING_PARENTHESIS = Pattern.compile("(?:" + CommentScanner.WHITESPACE_OR_COMMENT + "|;)*+\\)");

    public long endBefore(final StatementTree body,
                          final Tree compound,
                          final CompilationUnitTree cu,
                          final SourcePositions positions,
                          final String source) {
        return switch (compound) {
            case IfTree node when node.getThenStatement() == body -> positions.getEndPosition(cu, node.getCondition());
            case IfTree node -> after(positions.getEndPosition(cu, node.getThenStatement()), "else", source);
            case WhileLoopTree node -> positions.getEndPosition(cu, node.getCondition());
            case DoWhileLoopTree node -> after(positions.getStartPosition(cu, node), "do", source);
            case EnhancedForLoopTree node -> pastTheClosingParenthesis(positions.getEndPosition(cu, node.getExpression()), source);
            case ForLoopTree node -> pastTheClosingParenthesis(lastHeaderPartEnd(node, cu, positions, source), source);
            default -> -1;
        };
    }

    private long after(final long from, final String keyword, final String source) {
        if (from < 0) {
            return -1;
        }
        final var at = CommentScanner.skipWhitespaceAndComments(source, (int) from);
        return source.startsWith(keyword, at) ? at + keyword.length() : -1;
    }

    private long lastHeaderPartEnd(final ForLoopTree node, final CompilationUnitTree cu, final SourcePositions positions, final String source) {
        return Stream.<List<? extends Tree>>of(
                        node.getInitializer(), node.getCondition() == null ? List.of() : List.of(node.getCondition()), node.getUpdate())
                .flatMap(List::stream)
                .mapToLong(part -> positions.getEndPosition(cu, part))
                .max()
                .orElseGet(() -> source.indexOf('(', (int) positions.getStartPosition(cu, node)) + 1L);
    }

    private long pastTheClosingParenthesis(final long from, final String source) {
        if (from < 0) {
            return -1;
        }
        final var matcher = EMPTY_PARTS_AND_THE_CLOSING_PARENTHESIS.matcher(source)
                .region((int) from, source.length());
        return matcher.lookingAt() ? matcher.end() : -1;
    }
}
