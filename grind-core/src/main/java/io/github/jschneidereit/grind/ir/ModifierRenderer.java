package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ModifiersTree;

import java.util.Arrays;
import java.util.EnumSet;
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
    private static final EnumSet<Modifier> KNOWN_MODIFIERS = EnumSet.copyOf(JLS_ORDER);

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
        final var unknown = flags.stream().filter(m -> !KNOWN_MODIFIERS.contains(m)).toList();
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
            // Annotations are left alone in v1 (see CLAUDE.md formatting rules); javac's
            // pretty-printer text is the contract here, not a fallback to fix. Split on '\n'
            // so each line lands in its own Doc.Text — the IR's no-newline invariant.
            annotations.stream()
                .flatMap(a -> Stream.<Doc>concat(splitToTextLines(a.toString()), Stream.of(new Doc.HardLine()))),
            Stream.of(doc)
        ));
    }

    private static Stream<Doc> splitToTextLines(final String text) {
        return Doc.intersperse(
            new Doc.HardLine(),
            Arrays.stream(text.split("\n", -1)).<Doc>map(Doc.Text::new));
    }

    private ModifierRenderer() {}
}
