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
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WildcardTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class TypeRenderers {
    private final int TAB_WIDTH = 4;

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
        if (text.contains("\"\"\"")) {
            return new Doc.Verbatim(text, sourceColumnOf(node, unit));
        }
        final var lines = text.split("\n", -1);
        final var expanded = IntStream.range(0, lines.length)
                .mapToObj(i -> i == 0 ? lines[i] : withTabsExpandedInTheIndent(lines[i]))
                .collect(Collectors.joining("\n"));
        final var start = unit.startOf(node);
        final var source = unit.source();
        return new Doc.Verbatim(expanded, expandedWidth(source.substring(source.lastIndexOf('\n', start - 1) + 1, start)));
    }

    private String withTabsExpandedInTheIndent(final String line) {
        final var indentEnd = (int) line.chars().takeWhile(c -> c == ' ' || c == '\t').count();
        return " ".repeat(expandedWidth(line.substring(0, indentEnd))) + line.substring(indentEnd);
    }

    private int expandedWidth(final String text) {
        return text.chars().reduce(0, (column, c) -> c == '\t' ? (column / TAB_WIDTH + 1) * TAB_WIDTH : column + 1);
    }

    /**
     * 0-indexed source column of {@code node}'s first character: distance from the preceding
     * {@code '\n'} (or start of file). The printer uses this to shift a relocated multi-line
     * literal's internal lines so it stays visually aligned with where it appeared in the
     * source.
     */
    private int sourceColumnOf(final LiteralTree node, final ParsedUnit unit) {
        final var start = unit.startOf(node);
        final var source = unit.source();
        final var previousNewline = source.lastIndexOf('\n', start - 1);
        return start - previousNewline - 1;
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
        return renderArrayDimensions(node, recursor);
    }

    Doc renderParameterizedType(final ParameterizedTypeTree node, final Recursor recursor) {
        final var arguments = node.getTypeArguments();
        if (arguments.isEmpty()) {
            return Doc.concat(recursor.scan(node.getType()), new Doc.Text("<>"));
        }
        // Deliberately not a WrappableList. A type-argument list sits to the left of whatever the
        // declaration goes on to say, so its group is decided before the method chain further right
        // has chosen to break; the fit check sees the whole statement and breaks here instead,
        // giving `List<\n Map.Entry<\n String,`. Type arguments wide enough to matter on their own
        // are rare, and this cure is worse.
        final var argumentDocs = Doc.intersperse(new Doc.Text(", "), arguments.stream().map(recursor::scan));
        return new Doc.Concat(
                Stream.concat(
                        Stream.of(recursor.scan(node.getType()), new Doc.Text("<")), Stream.concat(argumentDocs, Stream.<Doc>of(new Doc.Text(">")))));
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
            case EXTENDS_WILDCARD -> Doc.concat(new Doc.Text("? extends "), recursor.scan(node.getBound()));
            case SUPER_WILDCARD -> Doc.concat(new Doc.Text("? super "), recursor.scan(node.getBound()));
            case UNBOUNDED_WILDCARD -> new Doc.Text("?");
            default -> throw new IllegalStateException("unexpected wildcard kind: " + node.getKind());
        };
    }

    Doc renderTypeParameter(final TypeParameterTree node, final Recursor recursor) {
        // `class Box<@Marker T>` is legal and the annotations were simply never read, so six files
        // of java.base's reference classes came back refused rather than formatted.
        final var annotations = node.getAnnotations().stream().flatMap(a -> Stream.of(recursor.scan(a), new Doc.Text(" ")));
        final var nameDoc = (Doc) new Doc.Concat(Stream.concat(annotations, Stream.of(new Doc.Text(node.getName().toString()))));
        final var bounds = node.getBounds();
        if (bounds.isEmpty()) {
            return nameDoc;
        }
        final var boundDocs = Doc.intersperse(new Doc.Text(" & "), bounds.stream().map(recursor::scan));
        return new Doc.Concat(Stream.concat(Stream.of(nameDoc, new Doc.Text(" extends ")), boundDocs));
    }

    Doc renderAnnotatedType(final AnnotatedTypeTree node, final Recursor recursor) {
        final var annotations = node.getAnnotations().stream().flatMap(a -> Stream.of(recursor.scan(a), new Doc.Text(" ")));
        if (node.getUnderlyingType() instanceof ArrayTypeTree) {
            return renderArrayDimensions(node, recursor);
        }
        if (node.getUnderlyingType() instanceof MemberSelectTree qualified) {
            return renderQualifiedTypeAnnotation(qualified, annotations, recursor);
        }
        return new Doc.Concat(Stream.concat(annotations, Stream.of(recursor.scan(node.getUnderlyingType()))));
    }

    /**
     * A type-use annotation on an array type is written between the element type and the
     * brackets it applies to. {@code int @Nullable []} annotates the array; {@code @Nullable
     * int[]} annotates the element — a different type. javac nests one level per pair of brackets,
     * the outermost for the first pair written, and wraps a level in an {@link AnnotatedTypeTree}
     * when its brackets carry an annotation. Writing the levels from the outside in, after the
     * element type, puts every annotation back on its own brackets: {@code char[] @A []} and
     * {@code byte @A [][]} are different types, and recursing inward instead swapped them.
     */
    private Doc renderArrayDimensions(final Tree type, final Recursor recursor) {
        return renderDimensions(type, "[]", recursor);
    }

    Doc renderVarargsType(final Tree type, final Recursor recursor) {
        return renderDimensions(type, "...", recursor);
    }

    private Doc renderDimensions(final Tree type, final String innermost, final Recursor recursor) {
        final var levels = Stream.<@Nullable Tree>iterate(type, Objects::nonNull, TypeRenderers::componentOf).map(Objects::requireNonNull).toList();
        final var dimensions = levels.size() - 1;
        final var brackets = IntStream.range(0, dimensions)
                .mapToObj(i -> bracketsOf(levels.get(i), i + 1 == dimensions ? innermost : "[]", recursor));
        return Doc.concatOf(Stream.of(Stream.of(recursor.scan(levels.getLast())), brackets));
    }

    private Doc bracketsOf(final Tree level, final String brackets, final Recursor recursor) {
        if (!(level instanceof AnnotatedTypeTree annotated)) {
            return new Doc.Text(brackets);
        }
        final var annotations = annotated.getAnnotations().stream().flatMap(a -> Stream.of(new Doc.Text(" "), recursor.scan(a)));
        return Doc.concatOf(Stream.of(annotations, Stream.of(new Doc.Text(" " + brackets))));
    }

    @Nullable
    private Tree componentOf(final Tree type) {
        return switch (type) {
            case ArrayTypeTree array -> array.getType();
            case AnnotatedTypeTree annotated when annotated.getUnderlyingType() instanceof ArrayTypeTree array -> array.getType();
            default -> null;
        };
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
                        Stream.of(recursor.scan(qualified.getExpression()), new Doc.Text(".")), annotations,
                        Stream.of(new Doc.Text(qualified.getIdentifier().toString()))));
    }
}
