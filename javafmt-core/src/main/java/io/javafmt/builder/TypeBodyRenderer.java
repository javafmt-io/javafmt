package io.javafmt.builder;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.Pairs;
import io.javafmt.parser.CommentToken;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The shared shell of class/record/enum rendering: a header followed by either {@code {}} (no
 * members) or a braced, one-per-line member list.
 */
@UtilityClass
final class TypeBodyRenderer {

    /**
     * @param tail comments sitting after the last member and before the closing brace. They have
     *     no member to hang off, so nothing else would emit them and the file would come back
     *     refused — or, with verification off, silently short a comment.
     */
    Doc withBody(final ClassTree node, final Doc header, final List<Member> members, final List<CommentToken> tail, final Recursor recursor) {
        final var modifiers = node.getModifiers();
        final var braceLine = recursor.braceLine(node);
        final var body = members.isEmpty() && tail.isEmpty() && braceLine.isEmpty()
                ? Doc.concat(header, new Doc.Text(" {}"))
                : Doc.concatOf(
                        Stream.of(
                                Stream.of(header, new Doc.Text(" {")),
                                BlockRenderer.besideTheBrace(braceLine),
                                Stream.of(Doc.HARD_LINE),
                                BlockRenderer.belowTheBrace(braceLine),
                                separated(members, recursor),
                                tailLines(tail),
                                Doc.closeBrace()));
        return ModifierRenderer.prependOwnLineAnnotations(modifiers, body, recursor);
    }

    Stream<Doc> tailLines(final List<CommentToken> tail) {
        return tail.stream()
                .map(CommentDocs::renderComment)
                .map(Doc::indentedLine);
    }

    /**
     * The members, each on its own line, with a blank line between them where the rule below wants
     * one. Matches {@code JavaInputAstVisitor.addBodyDeclarations} in google-java-format: a method,
     * constructor, nested type or initializer block gets a blank line on each side, and so does a
     * field that carries Javadoc; between two plain fields the author's grouping is kept, because
     * those blank lines are how a reader is told which constants or fields belong together.
     */
    private Stream<Doc> separated(final List<Member> members, final Recursor recursor) {
        return members.stream()
                .map(member -> new Wanting(member, wantsBlankLineAround(member, recursor)))
                .gather(Pairs.<Wanting, Stream<Doc>>withPrevious((previous, current) -> memberLines(previous, current, recursor)))
                .flatMap(Function.identity());
    }

    private Stream<Doc> memberLines(final Optional<Wanting> previous, final Wanting current, final Recursor recursor) {
        final var line = Doc.indentedLine(current.member().doc());
        return previous.stream()
                        .anyMatch(p -> separatedByBlankLine(p, current, recursor))
                ? Stream.of(Doc.HARD_LINE, line)
                : Stream.of(line);
    }

    private boolean separatedByBlankLine(final Wanting previous, final Wanting current, final Recursor recursor) {
        if (current.wantsBlankLine() || previous.wantsBlankLine()) {
            return true;
        }
        final var previousTree = previous.member().tree();
        final var currentTree = current.member().tree();
        return previousTree != null && currentTree != null && recursor.facts().blankLineBetween(previousTree, currentTree);
    }

    private boolean wantsBlankLineAround(final Member member, final Recursor recursor) {
        final var tree = member.tree();
        if (tree == null) {
            return true;
        }
        return !(tree instanceof VariableTree) || recursor.facts().hasJavadoc(tree);
    }

    /** The member-collect pipeline all three type renderers repeat. */
    List<Member> renderMembers(final Stream<? extends Tree> memberStream, final String className, final Recursor recursor) {
        return memberStream.map(m -> new Member(m, BodyMemberRenderer.render(m, className, recursor)))
                .toList();
    }

    private record Wanting(Member member, boolean wantsBlankLine) {}

    /** One rendered member of a type body, kept with its tree so the separation rule can read it. */
    record Member(@Nullable Tree tree, Doc doc) {}
}
