package io.javafmt.builder;

import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

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
    Doc withBody(final ModifiersTree mods, final Doc header, final List<Member> members, final List<CommentToken> tail, final Recursor recursor) {
        if (members.isEmpty() && tail.isEmpty()) {
            return ModifierRenderer.prependOwnLineAnnotations(mods, Doc.concat(header, new Doc.Text(" {}")), recursor);
        }
        return ModifierRenderer.prependOwnLineAnnotations(
                mods,
                Doc.concatOf(
                        Stream.of(
                                Stream.of(header, new Doc.Text(" {"), Doc.HARD_LINE),
                                separated(members, recursor),
                                tail.stream().map(CommentDocs::renderComment).map(Doc::indentedLine),
                                Doc.closeBrace())),
                recursor);
    }

    /**
     * The members, each on its own line, with a blank line between them where the rule below wants
     * one. Matches {@code JavaInputAstVisitor.addBodyDeclarations} in google-java-format: a method,
     * constructor, nested type or initializer block gets a blank line on each side, and so does a
     * field that carries Javadoc; between two plain fields the author's grouping is kept, because
     * those blank lines are how a reader is told which constants or fields belong together.
     */
    private Stream<Doc> separated(final List<Member> members, final Recursor recursor) {
        var previousWantedBlankLine = false;
        final var parts = new ArrayList<Doc>();
        for (var i = 0; i < members.size(); i++) {
            final var member = members.get(i);
            final var wantsBlankLine = wantsBlankLineAround(member, recursor);
            if (i > 0 && separatedByBlankLine(members.get(i - 1), member, wantsBlankLine, previousWantedBlankLine, recursor)) {
                parts.add(Doc.HARD_LINE);
            }
            parts.add(Doc.indentedLine(member.doc()));
            previousWantedBlankLine = wantsBlankLine;
        }
        return parts.stream();
    }

    private boolean separatedByBlankLine(
            final Member previous,
            final Member member,
            final boolean wantsBlankLine,
            final boolean previousWantedBlankLine,
            final Recursor recursor) {
        if (wantsBlankLine || previousWantedBlankLine) {
            return true;
        }
        return previous.tree() != null
            && member.tree() != null
            && recursor.blankLineBetween(previous.tree(), member.tree());
    }

    private boolean wantsBlankLineAround(final Member member, final Recursor recursor) {
        final var tree = member.tree();
        if (tree == null) {
            return true;
        }
        return !(tree instanceof VariableTree) || recursor.hasJavadoc(tree);
    }

    /** The member-collect pipeline all three type renderers repeat. */
    List<Member> renderMembers(final Stream<? extends Tree> memberStream, final String className, final Recursor recursor) {
        return memberStream.flatMap(
                m -> Optional.ofNullable(BodyMemberRenderer.render(m, className, recursor))
                        .map(doc -> new Member(m, doc))
                        .stream())
                .toList();
    }

    /** One rendered member of a type body, kept with its tree so the separation rule can read it. */
    record Member(@Nullable Tree tree, Doc doc) {}
}
