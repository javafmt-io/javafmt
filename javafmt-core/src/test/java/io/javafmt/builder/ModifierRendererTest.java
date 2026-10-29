package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import io.javafmt.doc.Doc;
import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

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
        final var mods = stubModifiers(flags, List.of());
        assertThat(ModifierRenderer.modifiersText(mods)).isEqualTo("public final sealed ");
    }

    @Test
    void prependOwnLineAnnotations_multilineAnnotationSource_isCollapsedOntoOneLine() {
        // Emitting the source lines as-is would not converge: each carries its original indentation
        // and the enclosing Indent adds more every pass. Collapsing keeps every token — an
        // author-written trailing comma included — while staying stable across passes.
        final var source = "@Foo({\n    \"a\",\n    \"b\",\n})";
        final var mods = stubModifiers(Set.of(), List.of(stubAnnotation(source)));
        final var doc = ModifierRenderer.prependOwnLineAnnotations(mods, new Doc.Text("class Bar"), stubRecursor(source));
        assertThat(new Printer(WIDTH).print(doc)).isEqualTo("@Foo({ \"a\", \"b\", })\nclass Bar");
    }

    @Test
    void rendersAnnotationsInline_singleArgumentlessAnnotationTheAuthorLeftInline() {
        final var mods = stubModifiers(Set.of(), List.of(stubAnnotation("@Override")));
        assertThat(ModifierRenderer.rendersAnnotationsInline(mods, stubRecursor("@Override", false))).isTrue();
    }

    @Test
    void rendersAnnotationsInline_noAnnotations() {
        assertThat(ModifierRenderer.rendersAnnotationsInline(stubModifiers(Set.of(), List.of()), stubRecursor("", false))).isFalse();
    }

    @Test
    void rendersAnnotationsInline_annotationTheAuthorPutOnItsOwnLine() {
        final var mods = stubModifiers(Set.of(), List.of(stubAnnotation("@Override")));
        assertThat(ModifierRenderer.rendersAnnotationsInline(mods, stubRecursor("@Override", true))).isFalse();
    }

    @Test
    void rendersAnnotationsInline_annotationWithArguments() {
        final var mods = stubModifiers(Set.of(), List.of(stubAnnotation("@SuppressWarnings(\"x\")", List.of(stubExpression()))));
        assertThat(ModifierRenderer.rendersAnnotationsInline(mods, stubRecursor("@SuppressWarnings(\"x\")", false))).isFalse();
    }

    @Test
    void rendersAnnotationsInline_moreThanOneAnnotation() {
        final var mods = stubModifiers(Set.of(), List.of(stubAnnotation("@A"), stubAnnotation("@B")));
        assertThat(ModifierRenderer.rendersAnnotationsInline(mods, stubRecursor("@A", false))).isFalse();
    }

    /** Only {@link Recursor#sourceOf} is exercised here; everything else is out of scope. */
    private static Recursor stubRecursor(final String source) {
        return stubRecursor(source, false);
    }

    /** Adds a canned {@link Recursor#isFollowedByNewline} answer for the inline-annotation predicate. */
    private static Recursor stubRecursor(final String source, final boolean followedByNewline) {
        return new Recursor() {

            @Override
            public String sourceOf(final com.sun.source.tree.Tree node) {
                return source;
            }

            @Override
            public CharSequence sourceSpan(final com.sun.source.tree.Tree node) {
                return source;
            }

            @Override
            public Doc scan(final com.sun.source.tree.Tree node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Doc attach(final com.sun.source.tree.Tree node, final Doc rendered) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<io.javafmt.parser.CommentToken> interior(final com.sun.source.tree.Tree node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Doc scanNullable(final com.sun.source.tree.Tree node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Doc scanCompact(final com.sun.source.tree.Tree node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean inCompactContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void emitWarning(final String message, final com.sun.source.tree.Tree at) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isVarargs(final com.sun.source.tree.VariableTree param) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isCompactConstructor(final com.sun.source.tree.MethodTree ctor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isEnumConstant(final com.sun.source.tree.VariableTree member) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean blankLineBetween(final com.sun.source.tree.Tree before, final com.sun.source.tree.Tree after) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasJavadoc(final com.sun.source.tree.Tree node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isFollowedByNewline(final com.sun.source.tree.Tree node) {
                return followedByNewline;
            }
        };
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
        return stubAnnotation(prettyPrinted, List.of());
    }

    private static AnnotationTree stubAnnotation(final String prettyPrinted, final List<? extends ExpressionTree> arguments) {
        return new AnnotationTree() {

            @Override
            public Tree getAnnotationType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<? extends ExpressionTree> getArguments() {
                return arguments;
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

    /** Stands in for an annotation argument; only its presence in the list matters here. */
    private static ExpressionTree stubExpression() {
        return new ExpressionTree() {

            @Override
            public Tree.Kind getKind() {
                return Tree.Kind.STRING_LITERAL;
            }

            @Override
            public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
                return null;
            }
        };
    }
}
