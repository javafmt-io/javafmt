package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.CommentScanner;
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
        final var signature = renderSignature(leading, parametersOf(node), node.getThrows(), recursor);
        final var signatureDoc = appendBody(signature, node, recursor);
        return ModifierRenderer.finish(preamble, signatureDoc, recursor);
    }

    List<VariableTree> parametersOf(final MethodTree node) {
        return Stream.concat(Stream.ofNullable(node.getReceiverParameter()), node.getParameters().stream()).toList();
    }

    /**
     * The return type, rendered through the Doc pipeline rather than javac's pretty-printer, so
     * it follows the same rules as parameter types — notably type-use annotation placement on
     * array types — and any comment inside it survives.
     */
    private Stream<Doc> returnTypeDoc(final MethodTree node, final Recursor recursor) {
        return node.getReturnType() == null ? Stream.empty() : Stream.of(recursor.scan(node.getReturnType()), new Doc.Text(" "));
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
        final var name = p.getNameExpression() == null ? new Doc.Text(Names.of(p)) : recursor.scan(p.getNameExpression());
        if (p.getType() == null) {
            return Doc.concat(new Doc.Text("var "), name);
        }
        final var typeDoc = recursor.isVarargs(p) ? TypeRenderers.renderVarargsType(p.getType(), recursor) : recursor.scan(p.getType());
        return Doc.concat(typeDoc, new Doc.Text(" "), name);
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
        final var statements = BlockRenderer.statementDocs(body.getStatements(), recursor).toList();
        final var block = BlockRenderer.buildBlock(
                Doc.concat(header, new Doc.Text(" {")), recursor.braceLine(body), statements, recursor.trailingInside(body));
        if (statements.size() != 1 || !writtenOnOneLine(node, body, recursor)) {
            return block;
        }
        final var oneLine = Doc.concat(header, new Doc.Text(" {"), statements.getFirst(), new Doc.Text("}"));
        return Doc.flatWidthOrBreak(oneLine) < 0 ? block : new Doc.Group(new Doc.IfBreak(block, oneLine));
    }

    private boolean writtenOnOneLine(final MethodTree node, final BlockTree body, final Recursor recursor) {
        final var declaration = recursor.sourceOf(node);
        return recursor.sourceOf(body).lines().count() == 1 && CommentScanner.withoutComments(declaration).equals(declaration);
    }
}
