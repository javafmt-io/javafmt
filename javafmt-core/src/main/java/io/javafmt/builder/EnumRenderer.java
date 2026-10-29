package io.javafmt.builder;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import io.javafmt.parser.CommentScanner;
import io.javafmt.parser.CommentToken;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class EnumRenderer {

    Doc render(final ClassTree node, final Recursor recursor) {
        final var prefix = ModifierRenderer.modifiersText(node.getModifiers()) + "enum " + node.getSimpleName();
        final var headerDoc = ClassLikeRenderer.buildTypeDeclHeader(
                new Doc.Text(prefix),
                null,
                node.getImplementsClause(),
                false,
                List.of(),
                recursor);
        final var constants = node.getMembers()
                .stream()
                .filter(m -> isConstant(m, recursor))
                .map(m -> (VariableTree) m)
                .toList();
        final var className = node.getSimpleName().toString();
        final var bodyMemberStream = node.getMembers().stream().filter(m -> !isConstant(m, recursor));
        final var bodyMembers = TypeBodyRenderer.renderMembers(bodyMemberStream, className, recursor);
        if (constants.isEmpty() && bodyMembers.isEmpty()) {
            return TypeBodyRenderer.withBody(node.getModifiers(), headerDoc, List.of(), recursor.trailingInside(node), recursor);
        }
        if (bodyMembers.isEmpty()) {
            final var pastTheLastSeparator = recursor.interior(node);
            final var constantsInner = new Doc.Indent(
                    Doc.concatOf(
                            Stream.of(
                                    Stream.of(Doc.LINE),
                                    CommentDocs.commaList(constants, recursor, c -> recursor.attach(c, renderConstant(c, recursor))),
                                    Stream.of(trailingSeparator(node, recursor)),
                                    pastTheLastSeparator.stream().flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c))),
                                    commentsAfterTheLastConstant(node, recursor))));
            return ModifierRenderer.prependOwnLineAnnotations(
                    node.getModifiers(),
                    new Doc.Group(
                            Doc.concat(
                                    headerDoc,
                                    new Doc.Text(" {"),
                                    new Doc.IfBreak(Doc.HARD_LINE, new Doc.Text("")),
                                    constantsInner,
                                    beforeTheClosingBrace(pastTheLastSeparator),
                                    new Doc.Text("}"))),
                    recursor);
        }
        // The constants go in as one member with no tree of its own, which the separation rule
        // reads as "always blank-line separated" — the right answer for a block of constants
        // followed by declarations.
        final var constantsBlock = new TypeBodyRenderer.Member(null, constantsBlockBeforeBodyMembers(constants, recursor));
        final var members = constants.isEmpty() ? bodyMembers : Stream.concat(Stream.of(constantsBlock), bodyMembers.stream()).toList();
        return TypeBodyRenderer.withBody(node.getModifiers(), headerDoc, members, recursor.tail(node), recursor);
    }

    /**
     * The constants as one combined member, dense with no blank lines between them, so the
     * blank-line-per-member spacing {@link TypeBodyRenderer#withBody} gives every other entry
     * lands only between this block and the first body member, and between body members
     * themselves. Always multi-line; the last constant uses {@code ;} as the required separator
     * before body declarations, every other constant uses {@code ,}.
     */
    private Doc constantsBlockBeforeBodyMembers(final List<VariableTree> constants, final Recursor recursor) {
        final var n = constants.size();
        return new Doc.Concat(
                Doc.intersperse(
                        Doc.HARD_LINE,
                        IntStream.range(0, n)
                                .<Doc>mapToObj(
                                        i -> i < n - 1
                                                ? constantAndComma(constants.get(i), recursor.interior(constants.get(i + 1)), recursor)
                                                : lastConstant(constants.get(i), recursor))));
    }

    /**
     * One constant and its {@code ,}, followed by whatever the author wrote past that comma on the
     * same line. The comment arrives on the <em>next</em> constant's {@code interior} — the channel
     * a comma-separated list uses to say "emit this after the separator" — because writing a
     * {@code //} comment before the comma would comment the comma out.
     */
    private Doc constantAndComma(final VariableTree constant, final List<CommentToken> pastTheComma, final Recursor recursor) {
        final var withComma = recursor.attach(constant, Doc.concat(renderConstant(constant, recursor), new Doc.Text(",")));
        return new Doc.Concat(
                Stream.concat(Stream.of(withComma), pastTheComma.stream().flatMap(c -> Stream.of(new Doc.Text(" "), CommentDocs.renderComment(c)))));
    }

    /**
     * The last constant before the body declarations, with the {@code ;} the body needs.
     *
     * <p>The {@code ;} goes on a line of its own when the constant carries a trailing comment.
     * {@link LeadingCommentAttacher#attach} writes a trailing comment after everything else on the
     * line, so a {@code ;} emitted with the constant ends up in front of it — and a comment after
     * the {@code ;} no longer belongs to the constant but to whatever declaration comes next, which
     * is where the following pass put it. {@code java/lang/reflect/AccessFlag.java} moved its
     * {@code // did not exist} down a line on every run.
     */
    private Doc lastConstant(final VariableTree constant, final Recursor recursor) {
        final var rendered = renderConstant(constant, recursor);
        return recursor.trailing(constant).isEmpty()
                ? recursor.attach(constant, Doc.concat(rendered, new Doc.Text(";")))
                : Doc.concat(recursor.attach(constant, rendered), Doc.HARD_LINE, new Doc.Text(";"));
    }

    /**
     * The break before the closing brace. Hard once a comment sits on the last separator's line,
     * both so the enum cannot print flat with its brace inside a {@code //} comment and because a
     * {@link Doc#LINE} there would be a second newline after the one the comment already needs,
     * leaving a blank line above the brace.
     */
    private Doc beforeTheClosingBrace(final List<CommentToken> pastTheLastSeparator) {
        return pastTheLastSeparator.isEmpty() ? Doc.LINE : Doc.HARD_LINE;
    }

    /**
     * The comments the author left on their own line after the last constant of an enum with no
     * body declarations. A hard line each, so an enum carrying one never prints flat and folds the
     * closing brace into a {@code //} comment. {@code java/lang/StackStreamFactory.java} documents
     * each of its three {@code WalkerState} constants this way and javafmt dropped the last one.
     *
     * <p>Only {@link LeadingCommentAttacher#tail}: a comment on the last separator's own line
     * arrives on {@code interior} instead, and the trailing separator has already written it there.
     */
    private Stream<Doc> commentsAfterTheLastConstant(final ClassTree node, final Recursor recursor) {
        return recursor.tail(node)
                .stream()
                .flatMap(c -> Stream.of(Doc.HARD_LINE, CommentDocs.renderComment(c)));
    }

    /**
     * The separator after the last constant of a body-less enum, preserving whatever the author
     * wrote there.
     *
     * <p>Both a trailing {@code ,} and a trailing {@code ;} are optional and carry no meaning, but
     * they are tokens the author wrote. Emitting the usual break-only comma instead would delete
     * them whenever the enum fits on one line, and "javafmt does not delete what you wrote" is a
     * cheaper contract to keep than a list of blessed normalizations. With output verification on,
     * dropping one does not corrupt the file — it freezes it, returning the source unformatted with
     * an internal error, which is worse for the user than an unfashionable comma.
     *
     * <p>Nothing is invented here either. The trailing comma a wide enum gets is written into the
     * source by the {@code EnumTrailingComma} lint rule, so by the time the renderer runs it is a
     * separator the source already has; a comma conjured at print time would be a token the output
     * verifier has never seen and the file would come back refused.
     */
    private Doc trailingSeparator(final ClassTree node, final Recursor recursor) {
        return switch (authorsSeparator(recursor.sourceOf(node))) {
            case ';' -> new Doc.Text(";");
            case ',' -> new Doc.Text(",");
            default -> new Doc.Text("");
        };
    }

    /**
     * The last meaningful character before the enum's closing brace, or {@code 0} if the constants
     * run straight into it. Comments are stripped first so {@code A, B, // note} is still read as a
     * trailing comma — through {@link CommentScanner}, because the regex this used to use matched
     * a {@code /*} inside a string literal too and ate the rest of the enum.
     */
    private char authorsSeparator(final String enumSource) {
        final var close = enumSource.lastIndexOf('}');
        if (close <= 0) {
            return 0;
        }
        final var beforeBrace = CommentScanner.withoutComments(enumSource.substring(0, close)).stripTrailing();
        return beforeBrace.isEmpty() ? 0 : beforeBrace.charAt(beforeBrace.length() - 1);
    }

    private boolean isConstant(final Tree member, final Recursor recursor) {
        return member instanceof VariableTree v && recursor.isEnumConstant(v);
    }

    /**
     * Renders one constant: its annotations, its name, its constructor arguments, and — for a
     * constant that overrides behaviour — its class body. The synthetic {@code public static
     * final} flags javac attaches to every constant are deliberately not rendered.
     */
    private Doc renderConstant(final VariableTree v, final Recursor recursor) {
        final var prefix = ModifierRenderer.annotationsText(v.getModifiers(), recursor);
        final var head = Stream.<Doc>of(new Doc.Text(prefix + v.getName().toString()));
        if (!(v.getInitializer() instanceof NewClassTree nc)) {
            return new Doc.Concat(head);
        }
        final var args = nc.getArguments().isEmpty()
                ? Stream.<Doc>empty()
                : Stream.of(WrappableList.of("(", ",", ")", nc.getArguments().stream().map(recursor::scan)));
        final var body = nc.getClassBody() == null ? Stream.<Doc>empty() : Stream.of(ClassBodyRenderer.render(nc.getClassBody(), recursor));
        return new Doc.Concat(Stream.concat(head, Stream.concat(args, body)));
    }
}
