package io.javafmt.builder;

import com.sun.source.tree.ClassTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Renders the {@code { ... }} body that a class can carry without being a type declaration: the
 * body of an anonymous class at a {@code new} expression, and the body of an enum constant. javac
 * models both as a {@link ClassTree} hanging off a {@code NewClassTree}.
 */
@UtilityClass
final class ClassBodyRenderer {
    Doc render(final ClassTree body, final Recursor recursor) {
        final var members = body.getMembers().stream().flatMap(m -> Optional.ofNullable(recursor.scanNullable(m)).stream()).toList();
        final var indentedMembers = members.isEmpty()
                ? Stream.<Doc>empty()
                : Stream.<Doc>of(new Doc.Indent(Doc.concat(Doc.HARD_LINE, new Doc.Concat(Doc.intersperse(Doc.BLANK_LINE, members.stream())))));
        final var tailLines = TypeBodyRenderer.tailLines(recursor.trailingInside(body));
        return Doc.concatOf(
                Stream.of(
                        Stream.<Doc>of(new Doc.Text(" {")), BlockRenderer.braceLineComments(recursor.braceLine(body)), indentedMembers, tailLines,
                        Doc.closeBrace()));
    }
}
