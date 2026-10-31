package io.javafmt.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ModifiersTree;
import io.javafmt.JavaModifiers;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

@UtilityClass
final class ModifierRenderer {
    /**
     * The annotations and modifiers of a declaration as one prefix, each token followed by a
     * space so a type can be concatenated directly after it. Empty when the declaration has
     * neither.
     */
    String prefixOf(final ModifiersTree modifiers, final Recursor recursor) {
        return annotationsText(modifiers, recursor) + modifiersText(modifiers);
    }

    /**
     * The annotations of a declaration, each as it appears in the source and followed by a space
     * so a modifier or type can be concatenated directly after it. Rendering through
     * {@code AnnotationTree.toString()} would rebuild the annotation from the parse tree, which
     * drops comments inside the argument list and normalizes away punctuation the lint pass added
     * — {@code ArrayTrailingComma}'s trailing comma in particular, which made the lint pass and
     * the renderer permanently disagree about the output.
     */
    String annotationsText(final ModifiersTree modifiers, final Recursor recursor) {
        return modifiers.getAnnotations()
                .stream()
                .map(a -> annotationText(a, recursor) + " ")
                .collect(Collectors.joining());
    }

    /**
     * The text to emit for one annotation.
     *
     * <p>A single-line annotation renders from its own source, which is strictly more faithful than
     * javac's pretty-printer: it keeps interior comments and any punctuation the lint pass added.
     *
     * <p>A multi-line annotation is collapsed onto one line, still from source. Two alternatives
     * were tried and rejected. Emitting its source lines as-is does not converge: each line carries
     * the indentation it had in the source and the enclosing {@code Indent} adds more on every pass,
     * so the annotation marches right indefinitely. Falling back to the pretty-printer converges but
     * rebuilds the annotation from the tree, dropping an author-written trailing comma — which, with
     * output verification on, freezes the file rather than merely reformatting it. Collapsing keeps
     * every token, so nothing is lost and nothing freezes.
     *
     * <p>The cost is that an author's multi-line annotation layout is not preserved, and a collapsed
     * annotation has no wrap points, so a very long one can exceed the line width. Both want the
     * {@link Doc.Verbatim} machinery that text blocks use, which is the open P2 item for routing
     * annotations through the Doc pipeline.
     */
    private String annotationText(final AnnotationTree annotation, final Recursor recursor) {
        final var source = recursor.sourceOf(annotation);
        return source.indexOf('\n') < 0 ? source : collapseToOneLine(source);
    }

    /**
     * Joins the lines of a multi-line annotation with a single space. Deliberately no cosmetic
     * tidying of the result: a rule like "drop the space after {@code (}" is a blind string replace
     * that would also rewrite the inside of a string literal.
     */
    private String collapseToOneLine(final String source) {
        return source.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /**
     * The modifiers of a declaration (in JLS order), each followed by a space so a keyword or
     * type can be concatenated directly after it. Empty when the declaration has none.
     */
    String modifiersText(final ModifiersTree modifiers) {
        final var flags = modifiers.getFlags();
        if (flags.isEmpty()) {
            return "";
        }
        final var unknown = flags.stream()
                .filter((javax.lang.model.element.Modifier m) -> !JavaModifiers.KNOWN_MODIFIERS.contains(m))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown modifier(s): " + unknown);
        }
        return JavaModifiers.JLS_ORDER.stream().filter(flags::contains).map(Modifier::toString).collect(Collectors.joining(" ")) + " ";
    }

    /**
     * Whether a field or local declaration's annotations belong on the declaration line. Several
     * marker annotations may share it — {@code @Getter @Setter int x} is one declaration, not
     * three lines — so the author's layout decides, as it does everywhere else. What cannot stay
     * there is an annotation carrying arguments: it is long enough on its own to push the
     * declaration past the line width, and being rendered from source text it has no break point
     * inside it to give way at.
     */
    boolean rendersFieldAnnotationsInline(final ModifiersTree modifiers, final Recursor recursor) {
        final var annotations = modifiers.getAnnotations();
        return !annotations.isEmpty()
            && annotations.stream().allMatch(a -> a.getArguments().isEmpty() && !recursor.isFollowedByNewline(a));
    }

    /**
     * The shared preamble computation for a method, constructor, or compact constructor: the
     * annotations always go on their own lines above the signature (via {@link #finish}), so the
     * prefix to lead with is the modifiers alone.
     */
    Preamble preambleOf(final ModifiersTree modifiers, final Recursor recursor) {
        return preambleOf(modifiers, recursor, false);
    }

    /** {@link #preambleOf} with the caller's own answer to "do these render inline". */
    Preamble preambleOf(final ModifiersTree modifiers, final Recursor recursor, final boolean inline) {
        return new Preamble(inline ? prefixOf(modifiers, recursor) : modifiersText(modifiers), inline);
    }

    /** Closes out a {@link Preamble}: own-line annotations prepended, unless already rendered inline. */
    Doc finish(final Preamble preamble, final ModifiersTree modifiers, final Doc doc, final Recursor recursor) {
        return preamble.inline() ? doc : prependOwnLineAnnotations(modifiers, doc, recursor);
    }

    Doc prependOwnLineAnnotations(final ModifiersTree modifiers, final Doc doc, final Recursor recursor) {
        return prependOwnLineAnnotations(modifiers.getAnnotations(), doc, recursor);
    }

    /**
     * Annotation interiors are left alone (see {@code docs/rules.md}), so the author's own
     * source text is the contract — reconstructing from the parse tree would drop interior
     * comments and lint-added punctuation. Each annotation's text is split on {@code '\n'} so
     * every line lands in its own {@link Doc.Text}, which may not contain newlines.
     */
    Doc prependOwnLineAnnotations(final List<? extends AnnotationTree> annotations, final Doc doc, final Recursor recursor) {
        if (annotations.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(
                Stream.concat(
                        annotations.stream().flatMap(a -> Stream.concat(splitToTextLines(annotationText(a, recursor)), Stream.of(Doc.HARD_LINE))),
                        Stream.of(doc)));
    }

    private Stream<Doc> splitToTextLines(final String text) {
        return Doc.intersperse(Doc.HARD_LINE, Arrays.stream(text.split("\n", -1)).map(Doc.Text::new));
    }

    record Preamble(String prefix, boolean inline) {}
}
