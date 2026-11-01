package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@UtilityClass
final class ClassLikeRenderer {
    Doc render(final ClassTree node, final String keyword, final Recursor recursor) {
        final var prefix = Doc.concat(ModifierRenderer.modifiersDoc(node.getModifiers(), recursor), new Doc.Text(keyword + " " + node.getSimpleName()));
        final var isInterface = node.getKind() == Tree.Kind.INTERFACE;
        final var superclass = isInterface ? null : node.getExtendsClause();
        final var interfaces = node.getImplementsClause();
        final var permits = node.getPermitsClause();
        final var namePart = Doc.concatOf(Stream.of(Stream.of(prefix), MethodRenderer.typeParamsPart(node.getTypeParameters(), recursor)));
        final var headerDoc = buildTypeDeclarationHeader(namePart, superclass, interfaces, isInterface, permits, recursor);
        final var className = node.getSimpleName().toString();
        // Members render in source order. The utility-class constructor used to be pinned to the
        // bottom here; that is now the MemberOrder lint rule, so the reorder lands in the source
        // the output verifier compares against rather than looking like tokens moving underneath it.
        final var members = TypeBodyRenderer.renderMembers(bodyMembers(node), className, recursor);
        return TypeBodyRenderer.withBody(node, headerDoc, members, recursor.trailingInside(node), recursor);
    }

    Doc buildTypeDeclarationHeader(final Doc prefix,
                                   @Nullable final Tree superclass,
                                   final List<? extends Tree> interfaces,
                                   final boolean interfacesKeywordIsExtends,
                                   final List<? extends Tree> permits,
                                   final Recursor recursor) {
        if (superclass == null && interfaces.isEmpty() && permits.isEmpty()) {
            return prefix;
        }
        return new Doc.Group(
                Doc.concatOf(
                        Stream.of(
                                Stream.of(prefix),
                                Optional.ofNullable(superclass)
                                        .map(s -> Doc.continuationIndent(Doc.concat(Doc.LINE, new Doc.Text("extends "), recursor.scan(s))))
                                        .stream(),
                                typeList(interfacesKeywordIsExtends ? "extends " : "implements ", interfaces, recursor),
                                typeList("permits ", permits, recursor))));
    }

    private Stream<Doc> typeList(final String keyword, final List<? extends Tree> types, final Recursor recursor) {
        return types.isEmpty() ? Stream.empty() : Stream.of(buildTypeList(keyword, types, recursor));
    }

    Doc buildTypeList(final String keyword, final List<? extends Tree> types, final Recursor recursor) {
        final var rendered = IntStream.range(0, types.size())
                .mapToObj(i -> i < types.size() - 1 ? typeAndComma(types.get(i), types.get(i + 1), recursor) : new WrappableList.Element(recursor.scan(types.get(i)), false))
                .toList();
        final var typesInterior = new Doc.Concat(
                IntStream.range(0, rendered.size())
                        .boxed()
                        .flatMap(i -> i == 0 ? Stream.of(rendered.get(i).doc()) : Stream.of(rendered.get(i - 1).endsItsLine() ? Doc.HARD_LINE : Doc.LINE, rendered.get(i).doc()))
                        .toList());
        return Doc.continuationIndent(Doc.concat(Doc.LINE, new Doc.Text(keyword), new Doc.Group(new Doc.Indent(typesInterior))));
    }

    private WrappableList.Element typeAndComma(final Tree type, final Tree next, final Recursor recursor) {
        final var pastTheComma = recursor.pastTheSeparatorBefore(next);
        final var doc = CommentDocs.appendTrailing(recursor.scanBeforeComments(type, d -> Doc.concat(d, new Doc.Text(","))), pastTheComma);
        return new WrappableList.Element(doc, !pastTheComma.isEmpty() || Doc.endsWithLineComment(doc));
    }

    /**
     * Renders an implicit (unnamed) class — a Java 25 compact source file with no
     * explicit class declaration. The members are emitted at top level (no header, no
     * braces, no indentation wrapper), separated by blank lines, in declaration order.
     */
    Doc renderImplicit(final ClassTree node, final Recursor recursor) {
        final var className = node.getSimpleName().toString();
        final var members = bodyMembers(node)
                .flatMap(m -> Optional.ofNullable(BodyMemberRenderer.render(m, className, recursor)).stream())
                .toList();
        if (members.isEmpty()) {
            return new Doc.Text("");
        }
        return new Doc.Concat(Doc.intersperse(Doc.BLANK_LINE, members.stream()));
    }

    private Stream<? extends Tree> bodyMembers(final ClassTree node) {
        return node.getMembers()
                .stream()
                .filter(m -> m instanceof VariableTree || m instanceof MethodTree || m instanceof ClassTree || m instanceof BlockTree);
    }
}
