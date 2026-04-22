package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ModifiersTree;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

final class ModifierRenderer {

    private static final List<Modifier> JLS_ORDER = List.of(
        Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE, Modifier.ABSTRACT,
        Modifier.DEFAULT, Modifier.STATIC, Modifier.FINAL, Modifier.SEALED,
        Modifier.NON_SEALED, Modifier.TRANSIENT, Modifier.VOLATILE,
        Modifier.SYNCHRONIZED, Modifier.NATIVE, Modifier.STRICTFP
    );

    static void renderAnnotations(final ModifiersTree mods, final StringBuilder sb) {
        for (final var annotation : mods.getAnnotations()) {
            sb.append(annotation).append(" ");
        }
    }

    static void renderModifiers(final ModifiersTree mods, final StringBuilder sb) {
        final var flags = mods.getFlags();
        if (flags.isEmpty()) {
            return;
        }
        final var unknown = flags.stream().filter(m -> !JLS_ORDER.contains(m)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown modifier(s): " + unknown);
        }
        sb.append(JLS_ORDER.stream()
            .filter(flags::contains)
            .map(Modifier::toString)
            .collect(Collectors.joining(" ")));
        sb.append(" ");
    }

    static Doc prependOwnLineAnnotations(final ModifiersTree mods, final Doc doc) {
        final var annotations = mods.getAnnotations();
        if (annotations.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(
            annotations.stream()
                .flatMap(a -> Stream.<Doc>of(new Doc.Text(a.toString()), new Doc.HardLine())),
            Stream.of(doc)
        ));
    }

    private ModifierRenderer() {}
}
