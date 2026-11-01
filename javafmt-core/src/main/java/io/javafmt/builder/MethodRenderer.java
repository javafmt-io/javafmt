package io.javafmt.builder;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@UtilityClass
final class MethodRenderer {
    @Nullable
    static Doc render(final MethodTree node, final Recursor recursor) {
        if (node.getName().contentEquals("<init>")) {
            return null;
        }
        final var modifiers = node.getModifiers();
        final var typeParameters = node.getTypeParameters();
        final var preamble = ModifierRenderer.preambleOf(modifiers, typeParameters, recursor);
        final var afterTypeParams = Doc.concatOf(
                Stream.of(
                        Stream.of(ModifierRenderer.annotationsAfterTypeParameters(modifiers, typeParameters, recursor)),
                        returnTypeDoc(node, recursor), Stream.of(new Doc.Text(node.getName().toString()))));
        final var leading = buildLeading(preamble.prefix(), typeParameters, afterTypeParams, recursor);
        final var signature = renderSignature(leading, node.getParameters(), node.getThrows(), recursor);
        final var signatureDoc = appendBody(signature, node, recursor);
        return ModifierRenderer.finish(preamble, signatureDoc, recursor);
    }

    /**
     * The return type, rendered through the Doc pipeline rather than javac's pretty-printer, so
     * it follows the same rules as parameter types — notably type-use annotation placement on
     * array types — and any comment inside it survives.
     */
    private Stream<Doc> returnTypeDoc(final MethodTree node, final Recursor recursor) {
        return node.getReturnType() == null ? Stream.<Doc>empty() : Stream.of(recursor.scan(node.getReturnType()), new Doc.Text(" "));
    }

    Doc buildLeading(final Doc modifiers, final List<? extends TypeParameterTree> typeParams, final Doc afterTypeParams, final Recursor recursor) {
        if (typeParams.isEmpty()) {
            return Doc.concat(modifiers, afterTypeParams);
        }
        return Doc.concat(modifiers, renderTypeParams(typeParams, recursor), new Doc.Text(" "), afterTypeParams);
    }

    /**
     * The {@code <T>} list of a declaration, as a stream so a caller with none writes nothing at
     * all. Type declarations need this as much as methods do: {@code class Box<T>} rendered without
     * it is {@code class Box}, which is why generic types were the single largest source of refused
     * files on {@code java.base}.
     */
    Stream<Doc> typeParamsPart(final List<? extends TypeParameterTree> typeParams, final Recursor recursor) {
        return typeParams.isEmpty() ? Stream.empty() : Stream.of(renderTypeParams(typeParams, recursor));
    }

    private Doc renderTypeParams(final List<? extends TypeParameterTree> typeParams, final Recursor recursor) {
        final var interior = new Doc.Concat(
                Stream.concat(
                        Stream.of(Doc.SOFT_LINE), Doc.intersperse(List.of(new Doc.Text(","), Doc.LINE), typeParams.stream().map(recursor::scan))));
        return new Doc.Group(Doc.concat(new Doc.Text("<"), new Doc.Indent(interior), Doc.SOFT_LINE, new Doc.Text(">")));
    }

    Doc renderSignature(final Doc leading,
                        final List<? extends VariableTree> params,
                        final List<? extends ExpressionTree> throwsList,
                        final Recursor recursor) {
        if (params.isEmpty() && throwsList.isEmpty()) {
            return Doc.concat(leading, new Doc.Text("()"));
        }
        final var paramsPart = params.isEmpty() ? Doc.concat(leading, new Doc.Text("()")) : buildParamsPart(leading, params, recursor);
        final var groupedParamsPart = new Doc.Group(paramsPart);
        if (throwsList.isEmpty()) {
            return groupedParamsPart;
        }
        return new Doc.Group(Doc.concat(groupedParamsPart, ClassLikeRenderer.buildTypeList("throws ", throwsList, recursor)));
    }

    private Doc buildParamsPart(final Doc leading, final List<? extends VariableTree> params, final Recursor recursor) {
        final var interior = CommentDocs.commaList(params, recursor, p -> renderParam(p, recursor)).toList();
        return Doc.concat(leading, WrappableList.alignedOf("(", interior, ")"));
    }

    private Doc renderParam(final VariableTree p, final Recursor recursor) {
        return Doc.concat(ModifierRenderer.prefixOf(p.getModifiers(), recursor), typeAndName(p, recursor));
    }

    Doc typeAndName(final VariableTree p, final Recursor recursor) {
        final var varargs = recursor.isVarargs(p) ? varargsElementType(p.getType(), recursor) : Optional.<Doc>empty();
        if (varargs.isPresent()) {
            return Doc.concat(varargs.get(), new Doc.Text("... " + Names.of(p)));
        }
        final var typeDoc = p.getType() == null ? new Doc.Text("var") : recursor.scan(p.getType());
        return Doc.concat(typeDoc, new Doc.Text(" " + Names.of(p)));
    }

    /**
     * Everything a varargs parameter writes before the {@code ...}: the element type, plus any
     * type-use annotation on the array the ellipsis creates, which belongs between the two —
     * {@code String @N ... arguments}. javac reports the parameter's type as that array, wrapped in an
     * {@link AnnotatedTypeTree} when it carries an annotation, and rendering the wrapper as an
     * ordinary type writes {@code String @N []} and loses the varargs outright.
     */
    private Optional<Doc> varargsElementType(@Nullable final Tree type, final Recursor recursor) {
        if (type instanceof ArrayTypeTree arr) {
            return Optional.of(recursor.scan(arr.getType()));
        }
        if (type instanceof AnnotatedTypeTree annotated && annotated.getUnderlyingType() instanceof ArrayTypeTree arr) {
            return Optional.of(
                    Doc.concatOf(
                            Stream.of(
                                    Stream.of(recursor.scan(arr.getType()), new Doc.Text(" ")),
                                    annotated.getAnnotations()
                                            .stream()
                                            .flatMap(a -> Stream.of(recursor.scan(a), new Doc.Text(" "))))));
        }
        return Optional.empty();
    }

    Doc appendBody(final Doc header, final MethodTree node, final Recursor recursor) {
        if (node.getBody() == null) {
            // An annotation element's default is part of its declaration, not a body; nothing else
            // reads it, so a `String value() default "x";` came out as a required element.
            return Optional.ofNullable(node.getDefaultValue())
                    .map(value -> Doc.concat(header, new Doc.Text(" default "), recursor.scan(value), new Doc.Text(";")))
                    .orElseGet(() -> Doc.concat(header, new Doc.Text(";")));
        }
        final var body = node.getBody();
        final var statements = body.getStatements();
        return BlockRenderer.buildBlock(
                Doc.concat(header, new Doc.Text(" {")), recursor.braceLine(body), BlockRenderer.statementDocs(statements, recursor).toList(),
                recursor.trailingInside(body));
    }
}
