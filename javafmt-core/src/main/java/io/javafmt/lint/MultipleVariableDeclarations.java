package io.javafmt.lint;

import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import io.javafmt.parser.ParsedUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Splits {@code int a, b, c = 0;} into separate declarations. Detects groups by checking
 * whether consecutive {@link VariableTree} siblings in the same block or class share the
 * same type start offset, which javac produces for multi-variable declarations.
 *
 * <p>The split is made by replacing each separating {@code ,} with {@code ;} and a copy of the
 * shared modifiers-and-element-type prefix. Everything else — the declarators, their initializers,
 * the author's comments and line breaks — is left as written, because it is never rewritten. This
 * used to rebuild the whole declaration from modifiers, type, names and initializers, which meant
 * declining outright whenever a comment sat anywhere in the span, since the rebuild had nowhere to
 * put it; eleven files of {@code java.base} then reached the renderer as groups it could not print.
 *
 * <p>C-style array suffixes belong to one declarator, not to the group: in {@code int a, b[];}
 * only {@code b} is an array. Since each declarator keeps its own text, its brackets come along
 * with it, and {@link ArrayTypeStyle} moves them to the type on a later iteration.
 */
public final class MultipleVariableDeclarations implements LintRule {

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        return LintScan.edits(
                ctx,
                edits -> new TreePathScanner<>() {

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
                });
    }

    private static void splitGroups(final List<? extends Tree> stmts, final ParsedUnit unit, final List<LintEdit> edits) {
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
                edits.addAll(buildEdits(stmts.subList(i, j).stream().map(t -> (VariableTree) t).toList(), unit));
            }
            i = j;
        }
    }

    private static List<LintEdit> buildEdits(final List<VariableTree> group, final ParsedUnit unit) {
        final var positions = unit.sourcePositions();
        final var root = unit.tree();
        final var first = group.getFirst();
        final var last = group.getLast();
        final var groupStart = (int) positions.getStartPosition(root, first);
        final var lastEnd = (int) positions.getEndPosition(root, last);
        if (groupStart < 0 || lastEnd < 0) {
            return List.of();
        }
        final var src = unit.source();
        final var spanEnd = declarationGroupEnd(src, lastEnd);
        if (spanEnd < 0) {
            return List.of();
        }
        final var indent = extractIndent(src, groupStart);
        final var elementTypeEnd = (int) positions.getEndPosition(root, elementTypeOf(first.getType()));
        if (elementTypeEnd <= groupStart || elementTypeEnd > spanEnd) {
            return List.of();
        }
        final var baseDims = countBaseDimensions(src, elementTypeEnd);
        if (baseDims < 0) {
            return List.of();
        }
        final var sharedModifiersAndElementType = src.substring(groupStart, elementTypeEnd) + "[]".repeat(baseDims);
        if (!CommentScanner.scan(sharedModifiersAndElementType).isEmpty()) {
            // The prefix is written once per declaration, so a comment inside it would be copied
            // along with it and the file would come back with the comment several times over.
            return List.of();
        }
        final var separators = separatorCommas(group, unit, src, groupStart, spanEnd);
        if (separators.size() != group.size() - 1) {
            // The declarators and the commas disagree, so something in this span is not what it
            // looks like. Leaving it alone is the only safe answer.
            return List.of();
        }
        final var prefix = "\n" + indent + sharedModifiersAndElementType + " ";
        return separators.stream()
                .flatMap(comma -> Stream.of(new LintEdit(comma, comma + 1, ";"), prefixEdit(src, comma, spanEnd, prefix)))
                .toList();
    }

    /**
     * The edit that writes the shared prefix in front of the next declarator's name, replacing the
     * whitespace that used to separate the two.
     *
     * <p>It starts after any comment following the comma rather than at the comma itself, so a
     * trailing {@code //} stays on the line of the declaration it was written against. Carrying it
     * along instead put it between the copied type and the name, where the rest of the declaration
     * was commented out.
     */
    private static LintEdit prefixEdit(final String src, final int comma, final int spanEnd, final String prefix) {
        var pos = comma + 1;
        var afterLastComment = pos;
        while (pos < spanEnd) {
            final var commentEnd = CommentScanner.skipComment(src, pos);
            if (commentEnd >= 0) {
                pos = commentEnd;
                afterLastComment = commentEnd;
            } else if (Character.isWhitespace(src.charAt(pos))) {
                pos++;
            } else {
                break;
            }
        }
        return new LintEdit(afterLastComment, pos, prefix);
    }

    /**
     * The offsets of the commas that separate the declarators, one per declarator but the last.
     *
     * <p>Each is found by walking back from the declarator's own end position, because javac puts
     * that end <em>past</em> the comma. Walking back finds the separator first, which is what makes
     * this safe for a declaration whose type or initializer contains commas of its own —
     * {@code Node<K, V> first, e;} has two commas and only the second separates anything.
     */
    private static List<Integer> separatorCommas(
            final List<VariableTree> group,
            final ParsedUnit unit,
            final String src,
            final int groupStart,
            final int spanEnd) {
        final var comments = CommentScanner.scan(src.substring(groupStart, spanEnd));
        final var commas = new ArrayList<Integer>();
        var lowerBound = groupStart;
        for (var k = 0; k < group.size() - 1; k++) {
            final var declaratorEnd = (int) unit.sourcePositions().getEndPosition(unit.tree(), group.get(k));
            final var comma = commaBefore(src, comments, groupStart, Math.min(declaratorEnd, spanEnd), lowerBound);
            if (comma < 0) {
                return List.of();
            }
            commas.add(comma);
            lowerBound = comma + 1;
        }
        return List.copyOf(commas);
    }

    private static int commaBefore(final String src, final List<CommentToken> comments, final int base, final int from, final int lowerBound) {
        for (var pos = Math.min(from, src.length()) - 1; pos >= lowerBound; pos--) {
            if (src.charAt(pos) == ',' && !insideAComment(comments, base, pos)) {
                return pos;
            }
        }
        return -1;
    }

    private static boolean insideAComment(final List<CommentToken> comments, final int base, final int pos) {
        return comments.stream().anyMatch(c -> base + c.start() <= pos && pos < base + c.end());
    }

    /**
     * The position just past the group's terminating {@code ;}, found by scanning forward from
     * {@code lastVariableEnd} — javac's {@code getEndPosition} for the last variable in a
     * multi-declaration already lands one position past the semicolon, so the scan starts one
     * back and typically finds it immediately. Returns -1 if no semicolon is found.
     */
    private static int declarationGroupEnd(final String src, final int lastVariableEnd) {
        var semiPos = lastVariableEnd - 1;
        while (semiPos < src.length() && src.charAt(semiPos) != ';') {
            semiPos++;
        }
        return semiPos >= src.length() ? -1 : semiPos + 1;
    }

    private static Tree elementTypeOf(final Tree type) {
        return type instanceof ArrayTypeTree array ? elementTypeOf(array.getType()) : type;
    }

    /**
     * Counts the {@code []} pairs written directly after the element type, which every declarator
     * in the group inherits. Returns -1 when something other than brackets or a declarator name
     * follows — a type-use annotation on a dimension, say — since the rebuild would drop it.
     */
    private static int countBaseDimensions(final String src, final int elementTypeEnd) {
        var pos = elementTypeEnd;
        var dims = 0;
        while (pos < src.length()) {
            final var commentEnd = CommentScanner.skipComment(src, pos);
            if (commentEnd >= 0) {
                // A comment between the type and the first name is the author's, not a dimension.
                // Reading it as neither left the whole group unsplit.
                pos = commentEnd;
            } else if (Character.isWhitespace(src.charAt(pos))) {
                pos++;
            } else if (src.startsWith("[]", pos)) {
                pos += 2;
                dims++;
            } else {
                return Character.isJavaIdentifierStart(src.charAt(pos)) ? dims : -1;
            }
        }
        return -1;
    }

    /**
     * Whether this variable is one declarator of a multi-variable declaration. Every rule that
     * rewrites a span the declarators share — the modifier list, the type — uses this to stay off
     * the group, because it would emit the same edit once per declarator and {@link LintEngine}
     * rejects that set as overlapping. This rule splits the group on the same round, so each
     * declaration becomes its own candidate on the next one.
     *
     * <p>A {@code var} declaration's type node is synthetic and has no source position, so it is
     * excluded rather than treated as a shared start — otherwise every {@code var} in the block
     * would be grouped together.
     */
    static boolean isPartOfDeclarationGroup(final TreePath path, final ParsedUnit unit) {
        final var parent = path.getParentPath();
        if (parent == null) {
            return false;
        }
        final var siblings = switch (parent.getLeaf()) {
            case BlockTree block -> List.<Tree>copyOf(block.getStatements());
            case ClassTree cls -> List.<Tree>copyOf(cls.getMembers());
            default -> List.<Tree>of();
        };
        return isPartOfDeclarationGroup(siblings, path.getLeaf(), unit);
    }

    /** The same question asked of a member whose sibling list the caller already has in hand. */
    static boolean isPartOfDeclarationGroup(final List<? extends Tree> siblings, final Tree member, final ParsedUnit unit) {
        if (!(member instanceof VariableTree variable)) {
            return false;
        }
        final var typeStart = typeStartOf(variable, unit);
        if (typeStart < 0) {
            return false;
        }
        final var index = siblings.indexOf(member);
        return index >= 0
            && IntStream.of(index - 1, index + 1)
                    .filter(i -> i >= 0 && i < siblings.size())
                    .anyMatch(i -> siblings.get(i) instanceof VariableTree sibling && typeStartOf(sibling, unit) == typeStart);
    }

    private static int typeStartOf(final VariableTree variable, final ParsedUnit unit) {
        return (int) unit.sourcePositions().getStartPosition(unit.tree(), variable.getType());
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
