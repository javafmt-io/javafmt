package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;

import io.github.jschneidereit.grind.parser.JavaParser;
import io.github.jschneidereit.grind.printer.Printer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

import org.junit.jupiter.api.Test;

class ModifierRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return Printer.print(DocBuilder.build(JavaParser.parseUnit(source)), WIDTH);
    }

    @Test
    void modifiersEmittedInJlsOrder() {
        assertThat(format("class Foo { static private final int X = 1; }"))
            .isEqualTo("class Foo {\n    private static final int X = 1;\n}");
    }

    @Test
    void modifiersEmittedInJlsOrder_sealedBeforeFinal_isReordered() {
        // SEALED (ordinal 6) precedes FINAL (ordinal 8) in the Modifier enum, but JLS mandates FINAL first.
        // This combination is invalid Java, but tests the ordering logic directly without a parser round-trip.
        final Set<Modifier> flags = EnumSet.of(Modifier.PUBLIC, Modifier.FINAL, Modifier.SEALED);
        final var mods = stubModifiers(flags);
        final var sb = new StringBuilder();
        ModifierRenderer.renderModifiers(mods, sb);
        assertThat(sb.toString()).isEqualTo("public final sealed ");
    }

    private static ModifiersTree stubModifiers(final Set<Modifier> flags) {
        return new ModifiersTree() {
            @Override
            public Set<Modifier> getFlags() {
                return flags;
            }

            @Override
            public List<? extends AnnotationTree> getAnnotations() {
                return List.of();
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
