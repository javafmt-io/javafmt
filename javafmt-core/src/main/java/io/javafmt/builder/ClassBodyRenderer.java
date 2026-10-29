package io.javafmt.builder;

import com.sun.source.tree.ClassTree;
import io.javafmt.doc.Doc;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Renders the {@code { ... }} body that a class can carry without being a type declaration: the
 * body of an anonymous class at a {@code new} expression, and the body of an enum constant. javac
 * models both as a {@link ClassTree} hanging off a {@code NewClassTree}.
 */
@UtilityClass
final class ClassBodyRenderer {

    Doc render(final ClassTree body, final Recursor recursor) {
        final var members = body.getMembers()
                .stream()
                .flatMap(m -> Optional.ofNullable(recursor.scanNullable(m)).stream())
                .<Doc>map(d -> new Doc.Indent(Doc.concat(Doc.BLANK_LINE, d)));
        return Doc.concatOf(Stream.of(Stream.<Doc>of(new Doc.Text(" {")), members, Doc.closeBrace()));
    }
}
