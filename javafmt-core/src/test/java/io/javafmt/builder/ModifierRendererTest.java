package io.javafmt.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ModifierRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void modifiersEmittedInJlsOrder() {
        assertThat(format("class Foo { static private final int X = 1; }")).isEqualTo("class Foo {\n\n    private static final int X = 1;\n}");
    }

    @Test
    void modifiersEmittedInJlsOrder_sealedBeforeFinal_isReordered() {
        // SEALED (ordinal 6) precedes FINAL (ordinal 8) in the Modifier enum, but JLS mandates FINAL first.
        // This combination is invalid Java, but tests the ordering logic directly without a parser round-trip.
        final var flags = EnumSet.of(Modifier.PUBLIC, Modifier.FINAL, Modifier.SEALED);
        final var modifiers = stubModifiers(flags, List.of());
        assertThat(ModifierRenderer.modifiersText(modifiers)).isEqualTo("public final sealed ");
    }

    @Test
    void aMultiLineAnnotationThatFitsIsJoinedOntoOneLine() {
        assertThat(format("@Foo({\n    \"a\",\n    \"b\"\n})\nclass Bar {}")).isEqualTo("@Foo({\"a\", \"b\"})\nclass Bar {}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Foo {\n\n    public @Nullable String find() {\n        return null;\n    }\n}",
            "class Foo {\n\n    public static <T> @Nullable T first() {\n        return null;\n    }\n}",
            "class Foo {\n\n    private final @Nullable String name;\n}",
            "class Foo {\n\n    void accept(final @Nullable String name) {}\n}",
            "public @Deprecated class Foo {}",
    })
    void anAnnotationWrittenAfterTheModifiersStaysThere(final String source) {
        assertThat(format(source)).isEqualTo(source);
    }

    @Test
    void anAnnotationInFrontOfTheModifiersOfAMethodTakesItsOwnLine() {
        assertThat(format("class Foo {\n\n    @Deprecated public @Nullable String find() {\n        return null;\n    }\n}"))
                .isEqualTo("class Foo {\n\n    @Deprecated\n    public @Nullable String find() {\n        return null;\n    }\n}");
    }

    private static ModifiersTree stubModifiers(final Set<Modifier> flags, final List<? extends AnnotationTree> annotations) {
        return new ModifiersTree() {
            @Override
            public Set<Modifier> getFlags() {
                return flags;
            }

            @Override
            public List<? extends AnnotationTree> getAnnotations() {
                return annotations;
            }

            @Override
            public Tree.Kind getKind() {
                return Tree.Kind.MODIFIERS;
            }

            @Override
            public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
                return null;
            }
        };
    }
}
