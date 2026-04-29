package io.github.jschneidereit.grind.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;

import io.github.jschneidereit.grind.doc.Doc;
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
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source)));
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
        final var mods = stubModifiers(flags, List.of());
        final var sb = new StringBuilder();
        ModifierRenderer.renderModifiers(mods, sb);
        assertThat(sb.toString()).isEqualTo("public final sealed ");
    }

    @Test
    void prependOwnLineAnnotations_multilineAnnotationToString_doesNotInjectNewlinesIntoText() {
        // javac's pretty-printer is single-line for the AnnotationTrees javac builds today,
        // but Doc.Text rejects newline characters and we route the annotation toString through
        // it directly. A future toString change (or a non-javac AnnotationTree) with embedded
        // newlines would crash the printer; defend the invariant by splitting on '\n' and
        // emitting interleaved HardLines.
        final var multiLine = stubAnnotation("@Foo({\n    \"a\",\n    \"b\"\n})");
        final var mods = stubModifiers(Set.of(), List.of(multiLine));

        final var doc = ModifierRenderer.prependOwnLineAnnotations(mods, new Doc.Text("class Bar"));

        assertThat(new Printer(WIDTH).print(doc))
            .isEqualTo("@Foo({\n    \"a\",\n    \"b\"\n})\nclass Bar");
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

    private static AnnotationTree stubAnnotation(final String prettyPrinted) {
        return new AnnotationTree() {
            @Override
            public Tree getAnnotationType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<? extends ExpressionTree> getArguments() {
                return List.of();
            }

            @Override
            public Tree.Kind getKind() {
                return Tree.Kind.ANNOTATION;
            }

            @Override
            public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
                return null;
            }

            @Override
            public String toString() {
                return prettyPrinted;
            }
        };
    }
}
