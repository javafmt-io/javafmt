package io.javafmt.builder;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.TypeParameterTree;
import io.javafmt.JavaModifiers;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

@UtilityClass
final class ModifierRenderer {
    /**
     * The annotations and modifiers of a declaration as one prefix, each token followed by a
     * space so a type can be concatenated directly after it. Empty when the declaration has
     * neither. An annotation the author wrote after a modifier keyword stays after the keywords.
     */
    Doc prefixOf(final ModifiersTree modifiers, final Recursor recursor) {
        return Doc.concat(annotationsDoc(declarationAnnotations(modifiers, List.of(), recursor), recursor), modifiersDoc(modifiers, recursor));
    }

    Doc annotationsDoc(final List<? extends AnnotationTree> annotations, final Recursor recursor) {
        return new Doc.Concat(
                annotations.stream()
                        .map(recursor::scan)
                        .flatMap(annotation -> Stream.of(annotation, Doc.endsWithLineComment(annotation) ? Doc.HARD_LINE : new Doc.Text(" "))));
    }

    Doc modifiersDoc(final ModifiersTree modifiers, final Recursor recursor) {
        return modifiersDoc(modifiers, List.of(), recursor);
    }

    Doc modifiersDoc(final ModifiersTree modifiers, final List<? extends TypeParameterTree> typeParameters, final Recursor recursor) {
        return Doc.concat(
                new Doc.Text(modifiersText(modifiers)),
                annotationsDoc(
                        annotations(
                                modifiers,
                                a -> recursor.followsAModifierKeyword(modifiers, a) && !followsTypeParameters(a, typeParameters, recursor)),
                        recursor));
    }

    Doc annotationsAfterTypeParameters(final ModifiersTree modifiers, final List<? extends TypeParameterTree> typeParameters, final Recursor recursor) {
        return annotationsDoc(annotations(modifiers, a -> followsTypeParameters(a, typeParameters, recursor)), recursor);
    }

    private List<? extends AnnotationTree> declarationAnnotations(final ModifiersTree modifiers,
                                                                  final List<? extends TypeParameterTree> typeParameters,
                                                                  final Recursor recursor) {
        return annotations(
                modifiers, a -> !recursor.followsAModifierKeyword(modifiers, a) && !followsTypeParameters(a, typeParameters, recursor));
    }

    private List<? extends AnnotationTree> annotations(final ModifiersTree modifiers, final Predicate<AnnotationTree> placed) {
        return modifiers.getAnnotations().stream().filter(placed).toList();
    }

    private boolean followsTypeParameters(final AnnotationTree annotation,
                                          final List<? extends TypeParameterTree> typeParameters,
                                          final Recursor recursor) {
        return !typeParameters.isEmpty() && recursor.startsAfter(annotation, typeParameters.getFirst());
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
     * declaration past the line width. Only the annotations in front of the modifiers count; one
     * written after them stays beside the type whatever this answers.
     */
    boolean rendersFieldAnnotationsInline(final ModifiersTree modifiers, final Recursor recursor) {
        final var annotations = declarationAnnotations(modifiers, List.of(), recursor);
        return !annotations.isEmpty()
            && annotations.stream().allMatch(a -> a.getArguments().isEmpty() && !recursor.isFollowedByNewline(a));
    }

    /**
     * The shared preamble computation for a method, constructor, or compact constructor: the
     * annotations in front of the modifiers always go on their own lines above the signature (via
     * {@link #finish}), so the prefix to lead with is the modifiers and any annotation written after
     * them.
     */
    Preamble preambleOf(final ModifiersTree modifiers, final List<? extends TypeParameterTree> typeParameters, final Recursor recursor) {
        return new Preamble(modifiersDoc(modifiers, typeParameters, recursor), declarationAnnotations(modifiers, typeParameters, recursor));
    }

    /** {@link #preambleOf} with the caller's own answer to "do these render inline". */
    Preamble preambleOf(final ModifiersTree modifiers, final Recursor recursor, final boolean inline) {
        return inline
                ? new Preamble(prefixOf(modifiers, recursor), List.of())
                : new Preamble(modifiersDoc(modifiers, recursor), declarationAnnotations(modifiers, List.of(), recursor));
    }

    /** Closes out a {@link Preamble}: own-line annotations prepended, unless already rendered inline. */
    Doc finish(final Preamble preamble, final Doc doc, final Recursor recursor) {
        return prependOwnLineAnnotations(preamble.ownLine(), doc, recursor);
    }

    Doc prependOwnLineAnnotations(final ModifiersTree modifiers, final Doc doc, final Recursor recursor) {
        return prependOwnLineAnnotations(declarationAnnotations(modifiers, List.of(), recursor), doc, recursor);
    }

    Doc prependOwnLineAnnotations(final List<? extends AnnotationTree> annotations, final Doc doc, final Recursor recursor) {
        if (annotations.isEmpty()) {
            return doc;
        }
        return new Doc.Concat(Stream.concat(annotations.stream().flatMap(a -> Stream.of(recursor.scan(a), Doc.HARD_LINE)), Stream.of(doc)));
    }

    record Preamble(Doc prefix, List<? extends AnnotationTree> ownLine) {}
}
