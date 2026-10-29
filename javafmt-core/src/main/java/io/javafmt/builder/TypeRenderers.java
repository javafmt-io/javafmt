package io.javafmt.builder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnyPatternTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WildcardTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.ParsedUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class TypeRenderers {

    Doc renderIdentifier(final IdentifierTree node) {
        return new Doc.Text(node.getName().toString());
    }

    Doc renderPrimitiveType(final PrimitiveTypeTree node) {
        return new Doc.Text(node.getPrimitiveTypeKind().name().toLowerCase(Locale.ROOT));
    }

    Doc renderLiteral(final LiteralTree node, final ParsedUnit unit) {
        final var text = unit.sourceOf(node);
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return new Doc.Text(text);
        }
        return new Doc.Verbatim(text, sourceColumnOf(node, unit));
    }

    /**
     * 0-indexed source column of {@code node}'s first character: distance from the preceding
     * {@code '\n'} (or start of file). The printer uses this to shift a relocated multi-line
     * literal's internal lines so it stays visually aligned with where it appeared in the
     * source.
     */
    private int sourceColumnOf(final LiteralTree node, final ParsedUnit unit) {
        final var start = (int) unit.sourcePositions().getStartPosition(unit.tree(), node);
        final var source = unit.source();
        final var prevNewline = source.lastIndexOf('\n', start - 1);
        return start - prevNewline - 1;
    }

    Doc renderEmptyStatement(final EmptyStatementTree node) {
        return new Doc.Text(";");
    }

    Doc renderBreak(final BreakTree node) {
        return node.getLabel() == null ? new Doc.Text("break;") : new Doc.Text("break " + node.getLabel() + ";");
    }

    Doc renderContinue(final ContinueTree node) {
        return node.getLabel() == null ? new Doc.Text("continue;") : new Doc.Text("continue " + node.getLabel() + ";");
    }

    Doc renderDefaultCaseLabel(final DefaultCaseLabelTree node) {
        return new Doc.Text("default");
    }

    /** The unnamed pattern (JEP 456), as in {@code case Point(var x, _)}. */
    Doc renderAnyPattern(final AnyPatternTree node) {
        return new Doc.Text("_");
    }

    Doc renderArrayType(final ArrayTypeTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(recursor.scan(node.getType()), new Doc.Text("[]")));
    }

    Doc renderParameterizedType(final ParameterizedTypeTree node, final Recursor recursor) {
        final var args = node.getTypeArguments();
        if (args.isEmpty()) {
            return new Doc.Concat(List.of(recursor.scan(node.getType()), new Doc.Text("<>")));
        }
        // Deliberately not a WrappableList. A type-argument list sits to the left of whatever the
        // declaration goes on to say, so its group is decided before the method chain further right
        // has chosen to break; the fit check sees the whole statement and breaks here instead,
        // giving `List<\n Map.Entry<\n String,`. Type arguments wide enough to matter on their own
        // are rare, and this cure is worse.
        final var argDocs = Doc.intersperse(new Doc.Text(", "), args.stream().map(recursor::scan));
        return new Doc.Concat(
                Stream.concat(
                        Stream.of(recursor.scan(node.getType()), new Doc.Text("<")),
                        Stream.concat(argDocs, Stream.<Doc>of(new Doc.Text(">")))));
    }

    /**
     * The {@code A | B | C} of a multi-catch, wrapping with the {@code |} leading each continuation
     * line. {@code java/io/ObjectInputFilter.java} catches six exception types in one clause, on a
     * 174-character line.
     */
    Doc renderUnionType(final UnionTypeTree node, final Recursor recursor) {
        return WrappableList.joined(List.of(Doc.LINE, new Doc.Text("| ")), node.getTypeAlternatives().stream().map(recursor::scan));
    }

    Doc renderIntersectionType(final IntersectionTypeTree node, final Recursor recursor) {
        return new Doc.Concat(Doc.intersperse(new Doc.Text(" & "), node.getBounds().stream().map(recursor::scan)));
    }

    Doc renderWildcard(final WildcardTree node, final Recursor recursor) {
        return switch (node.getKind()) {
            case EXTENDS_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? extends "), recursor.scan(node.getBound())));
            case SUPER_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? super "), recursor.scan(node.getBound())));
            case UNBOUNDED_WILDCARD -> new Doc.Text("?");
            default -> throw new IllegalStateException("unexpected wildcard kind: " + node.getKind());
        };
    }

    Doc renderTypeParameter(final TypeParameterTree node, final Recursor recursor) {
        // `class Box<@Marker T>` is legal and the annotations were simply never read, so six files
        // of java.base's reference classes came back refused rather than formatted.
        final var annotations = node.getAnnotations()
                .stream()
                .flatMap(a -> Stream.of(recursor.scan(a), new Doc.Text(" ")));
        final var nameDoc = (Doc) new Doc.Concat(Stream.concat(annotations, Stream.of(new Doc.Text(node.getName().toString()))));
        final var bounds = node.getBounds();
        if (bounds.isEmpty()) {
            return nameDoc;
        }
        final var boundDocs = Doc.intersperse(new Doc.Text(" & "), bounds.stream().map(recursor::scan));
        return new Doc.Concat(Stream.concat(Stream.of(nameDoc, new Doc.Text(" extends ")), boundDocs));
    }

    Doc renderAnnotatedType(final AnnotatedTypeTree node, final Recursor recursor) {
        final var annotations = node.getAnnotations()
                .stream()
                .flatMap(a -> Stream.of(recursor.scan(a), new Doc.Text(" ")));
        if (node.getUnderlyingType() instanceof ArrayTypeTree arr) {
            return renderArrayDimensionAnnotation(arr, annotations, recursor);
        }
        if (node.getUnderlyingType() instanceof MemberSelectTree qualified) {
            return renderQualifiedTypeAnnotation(qualified, annotations, recursor);
        }
        return new Doc.Concat(Stream.concat(annotations, Stream.of(recursor.scan(node.getUnderlyingType()))));
    }

    /**
     * A type-use annotation on an array type is written between the element type and the
     * brackets it applies to. {@code int @Nullable []} annotates the array; {@code @Nullable
     * int[]} annotates the element — a different type. javac nests one {@link AnnotatedTypeTree}
     * per dimension, so recursing on the element type places each level's annotations correctly:
     * {@code int @A [] @B []}.
     */
    private Doc renderArrayDimensionAnnotation(final ArrayTypeTree arr, final Stream<Doc> annotations, final Recursor recursor) {
        return Doc.concatOf(Stream.of(Stream.of(recursor.scan(arr.getType()), new Doc.Text(" ")), annotations, Stream.of(new Doc.Text("[]"))));
    }

    /**
     * A type-use annotation on a qualified nested type binds to the simple name, so it is
     * written after the dot: {@code Outer.@Marker Inner}. Hoisting it to the front produces
     * {@code @Marker Outer.Inner}, which javac rejects outright — the annotation would be
     * claiming to annotate the enclosing type.
     */
    private Doc renderQualifiedTypeAnnotation(final MemberSelectTree qualified, final Stream<Doc> annotations, final Recursor recursor) {
        return Doc.concatOf(
                Stream.of(
                        Stream.of(recursor.scan(qualified.getExpression()), new Doc.Text(".")),
                        annotations,
                        Stream.of(new Doc.Text(qualified.getIdentifier().toString()))));
    }
}
