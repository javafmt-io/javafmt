package io.javafmt.builder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.WildcardTree;

import io.javafmt.doc.Doc;
import io.javafmt.parser.ParsedUnit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class TypeRenderers {

    static Doc renderIdentifier(final IdentifierTree node) {
        return new Doc.Text(node.getName().toString());
    }

    static Doc renderPrimitiveType(final PrimitiveTypeTree node) {
        return new Doc.Text(node.getPrimitiveTypeKind().name().toLowerCase(Locale.ROOT));
    }

    static Doc renderLiteral(final LiteralTree node, final ParsedUnit unit) {
        final var text = unit.sourceOf(node);
        if (text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return new Doc.Text(text);
        }
        return new Doc.Concat(Doc.intersperse(new Doc.HardLine(), Arrays.stream(text.split("\n", -1))
            .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
            .<Doc>map(Doc.Text::new)));
    }

    static Doc renderEmptyStatement(final EmptyStatementTree node) {
        return new Doc.Text(";");
    }

    static Doc renderBreak(final BreakTree node) {
        return node.getLabel() == null
            ? new Doc.Text("break;")
            : new Doc.Text("break " + node.getLabel() + ";");
    }

    static Doc renderContinue(final ContinueTree node) {
        return node.getLabel() == null
            ? new Doc.Text("continue;")
            : new Doc.Text("continue " + node.getLabel() + ";");
    }

    static Doc renderDefaultCaseLabel(final DefaultCaseLabelTree node) {
        return new Doc.Text("default");
    }

    static Doc renderArrayType(final ArrayTypeTree node, final Recursor recursor) {
        return new Doc.Concat(List.of(recursor.scan(node.getType()), new Doc.Text("[]")));
    }

    static Doc renderParameterizedType(final ParameterizedTypeTree node, final Recursor recursor) {
        final var args = node.getTypeArguments();
        if (args.isEmpty()) {
            return new Doc.Concat(List.of(recursor.scan(node.getType()), new Doc.Text("<>")));
        }
        final var argDocs = Doc.intersperse(new Doc.Text(", "), args.stream().<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(recursor.scan(node.getType()), new Doc.Text("<")),
            Stream.concat(argDocs, Stream.<Doc>of(new Doc.Text(">")))));
    }

    static Doc renderUnionType(final UnionTypeTree node, final Recursor recursor) {
        return new Doc.Concat(Doc.intersperse(new Doc.Text(" | "), node.getTypeAlternatives().stream()
            .<Doc>map(recursor::scan)));
    }

    static Doc renderIntersectionType(final IntersectionTypeTree node, final Recursor recursor) {
        return new Doc.Concat(Doc.intersperse(new Doc.Text(" & "), node.getBounds().stream()
            .<Doc>map(recursor::scan)));
    }

    static Doc renderWildcard(final WildcardTree node, final Recursor recursor) {
        return switch (node.getKind()) {
            case EXTENDS_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? extends "), recursor.scan(node.getBound())));
            case SUPER_WILDCARD -> new Doc.Concat(List.of(new Doc.Text("? super "), recursor.scan(node.getBound())));
            case UNBOUNDED_WILDCARD -> new Doc.Text("?");
            default -> throw new IllegalStateException("unexpected wildcard kind: " + node.getKind());
        };
    }

    static Doc renderTypeParameter(final TypeParameterTree node, final Recursor recursor) {
        final var nameDoc = (Doc) new Doc.Text(node.getName().toString());
        final var bounds = node.getBounds();
        if (bounds.isEmpty()) {
            return nameDoc;
        }
        final var boundDocs = Doc.intersperse(new Doc.Text(" & "), bounds.stream().<Doc>map(recursor::scan));
        return new Doc.Concat(Stream.concat(
            Stream.<Doc>of(nameDoc, new Doc.Text(" extends ")),
            boundDocs));
    }

    static Doc renderAnnotatedType(final AnnotatedTypeTree node, final Recursor recursor) {
        return new Doc.Concat(Stream.concat(
            node.getAnnotations().stream().<Doc>flatMap(a -> Stream.<Doc>of(recursor.scan(a), new Doc.Text(" "))),
            Stream.<Doc>of(recursor.scan(node.getUnderlyingType()))));
    }

    private TypeRenderers() {}
}
