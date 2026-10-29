package io.javafmt.lint;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.javafmt.parser.ParsedUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

/**
 * Moves nested types to the bottom of the body that declares them, and in a utility class moves the
 * private no-arg constructor below those. Nothing else is reordered.
 *
 * <p>Both moves are safe in a way general member reordering is not: a nested type executes no code
 * and can be referenced from anywhere in the enclosing body, and a constructor whose only job is to
 * prevent instantiation carries nothing a reader needs early. What they buy is a body that opens
 * with what the class *does* rather than with a page of helper records.
 *
 * <p>This is a lint rule and not a rendering decision because the output verifier compares the
 * printed text against the post-lint source. A reorder performed after lint moves tokens the
 * verifier has only ever seen in their original order, and it reports that as corruption; an edit
 * here lands in the source the verifier uses as its baseline. Same reason {@code EnumTrailingComma}
 * and the class-body half of {@code EmptyStatement} are rules rather than renderer behaviour.
 *
 * <p>Anonymous class bodies and local classes are left alone: their members are already scoped to
 * one expression or one method, and moving anything there buys nothing.
 */
public final class MemberOrder implements LintRule {

    private static final String CONSTRUCTOR_NAME = "<init>";

    @Override
    public LintResult apply(final LintContext ctx) {
        final var unit = ctx.unit();
        final var edits = new ArrayList<LintEdit>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitClass(final ClassTree node, final Void p) {
                if (!reorderable(getCurrentPath())) {
                    return super.visitClass(node, null);
                }
                final var own = editsFor(node, unit);
                edits.addAll(own);
                if (own.isEmpty()) {
                    return super.visitClass(node, null);
                }
                // A nested type that is moving is copied wholesale, so an edit inside it would land
                // in a span this round is about to delete — which the engine reports as overlapping
                // edits and refuses the whole file. The lint loop re-parses between rounds, so the
                // inner body gets its turn on the next pass.
                return null;
            }
        }.scan(unit.tree(), null);
        return LintResult.ofEdits(List.copyOf(edits));
    }

    private static boolean reorderable(final TreePath path) {
        final var parent = path.getParentPath();
        return parent == null || !(parent.getLeaf() instanceof NewClassTree || parent.getLeaf() instanceof BlockTree);
    }

    private static List<LintEdit> editsFor(final ClassTree node, final ParsedUnit unit) {
        final var members = node.getMembers().stream().filter(m -> start(unit, m) >= 0).toList();
        final var movers = membersToMove(node, members);
        if (movers.isEmpty() || alreadyInOrder(members, movers)) {
            return List.of();
        }
        final var spans = members.stream().map(m -> span(unit, m)).toList();
        final var insertAt = spans.getLast().end();
        final var indent = " ".repeat(columnOf(unit.source(), spans.getFirst().start()));
        final var appended = movers.stream()
                .map(m -> "\n\n" + indent + unit.source().substring(span(unit, m).start(), span(unit, m).end()))
                .reduce("", String::concat);
        return Stream.concat(
                movers.stream()
                        .map(m -> span(unit, m))
                        .map(s -> new LintEdit(s.start(), s.end(), "")),
                Stream.of(LintEdit.insert(insertAt, appended)))
                .toList();
    }

    /**
     * Every nested type, in declaration order, followed by the utility-class constructor. All the
     * nested types move whenever any one of them is out of place: moving only the stragglers would
     * shuffle them past the ones that stayed and lose the order the author wrote them in.
     */
    private static List<Tree> membersToMove(final ClassTree node, final List<? extends Tree> members) {
        final var nestedTypes = members.stream().filter(ClassTree.class::isInstance).<Tree>map(m -> m).toList();
        final var constructor = utilityConstructor(node, members);
        return constructor.isEmpty() ? nestedTypes : Stream.concat(nestedTypes.stream(), constructor.stream()).toList();
    }

    /** Whether {@code movers} are already the last members, in the order they would be moved into. */
    private static boolean alreadyInOrder(final List<? extends Tree> members, final List<Tree> movers) {
        return members.subList(members.size() - movers.size(), members.size()).equals(movers);
    }

    private static List<Tree> utilityConstructor(final ClassTree node, final List<? extends Tree> members) {
        if (node.getKind() != Tree.Kind.CLASS || !node.getModifiers().getFlags().contains(Modifier.FINAL)) {
            return List.of();
        }
        if (members.stream().anyMatch(MemberOrder::isInstanceField)) {
            return List.of();
        }
        final var constructors = members.stream()
                .filter(MemberOrder::isConstructor)
                .map(MethodTree.class::cast)
                .toList();
        if (constructors.size() != 1) {
            return List.of();
        }
        final var constructor = constructors.getFirst();
        final var flags = constructor.getModifiers().getFlags();
        return flags.contains(Modifier.PRIVATE) && constructor.getParameters().isEmpty() ? List.of(constructor) : List.of();
    }

    private static boolean isConstructor(final Tree member) {
        return member instanceof MethodTree m && m.getName().contentEquals(CONSTRUCTOR_NAME);
    }

    private static boolean isInstanceField(final Tree member) {
        return member instanceof VariableTree v && !v.getModifiers().getFlags().contains(Modifier.STATIC);
    }

    /**
     * A member's source span including its leading comments and Javadoc and any trailing comment on
     * its own last line, so a member that moves takes what the author wrote about it along.
     */
    private static Span span(final ParsedUnit unit, final Tree member) {
        final var leading = unit.leadingOf(member)
                .stream()
                .mapToInt(c -> c.start())
                .min()
                .orElse(start(unit, member));
        final var trailing = unit.trailingOf(member)
                .stream()
                .mapToInt(c -> c.end())
                .max()
                .orElse(end(unit, member));
        return new Span(Math.min(leading, start(unit, member)), Math.max(trailing, end(unit, member)));
    }

    private static int columnOf(final String source, final int pos) {
        return pos - (source.lastIndexOf('\n', pos - 1) + 1);
    }

    private static int start(final ParsedUnit unit, final Tree tree) {
        return (int) unit.sourcePositions().getStartPosition(unit.tree(), tree);
    }

    private static int end(final ParsedUnit unit, final Tree tree) {
        return (int) unit.sourcePositions().getEndPosition(unit.tree(), tree);
    }

    private record Span(int start, int end) {}
}
