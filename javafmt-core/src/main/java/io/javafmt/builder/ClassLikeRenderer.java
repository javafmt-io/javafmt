package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

@UtilityClass
final class ClassLikeRenderer {

    Doc render(final ClassTree node, final String keyword, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var prefix = ModifierRenderer.modifiersText(node.getModifiers()) + keyword + " " + node.getSimpleName();
        final var isInterface = node.getKind() == Tree.Kind.INTERFACE;
        final var superclass = isInterface ? null : node.getExtendsClause();
        final var interfaces = node.getImplementsClause();
        final var permits = node.getPermitsClause();
        final var namePart = Doc.concatOf(Stream.of(new Doc.Text(prefix)), MethodRenderer.typeParamsPart(node.getTypeParameters(), recursor));
        final var headerDoc = buildTypeDeclHeader(namePart, superclass, interfaces, isInterface, permits, recursor);
        final var className = node.getSimpleName().toString();
        // Members render in source order. The utility-class constructor used to be pinned to the
        // bottom here; that is now the MemberOrder lint rule, so the reorder lands in the source
        // the output verifier compares against rather than looking like tokens moving underneath it.
        final var memberStream = node.getMembers()
                .stream()
                .filter(m -> m instanceof VariableTree || m instanceof MethodTree || m instanceof ClassTree || m instanceof BlockTree);
        final var members = TypeBodyRenderer.renderMembers(memberStream, className, recursor, attacher, BodyMemberRenderer.PLAIN_CONSTRUCTOR);
        return TypeBodyRenderer.withBody(node.getModifiers(), headerDoc, members, attacher.trailingInside(node), recursor);
    }

    Doc buildTypeDeclHeader(
            final Doc prefix,
            @Nullable final Tree superclass,
            final List<? extends Tree> interfaces,
            final boolean interfacesKeywordIsExtends,
            final Recursor recursor) {
        return buildTypeDeclHeader(prefix, superclass, interfaces, interfacesKeywordIsExtends, List.of(), recursor);
    }

    Doc buildTypeDeclHeader(
            final Doc prefix,
            @Nullable final Tree superclass,
            final List<? extends Tree> interfaces,
            final boolean interfacesKeywordIsExtends,
            final List<? extends Tree> permits,
            final Recursor recursor) {
        if (superclass == null && interfaces.isEmpty() && permits.isEmpty()) {
            return prefix;
        }
        final var parts = new ArrayList<Doc>();
        parts.add(prefix);
        if (superclass != null) {
            parts.add(Doc.continuationIndent(new Doc.Concat(List.of(Doc.LINE, new Doc.Text("extends "), recursor.scan(superclass)))));
        }
        if (!interfaces.isEmpty()) {
            final var keyword = interfacesKeywordIsExtends ? "extends " : "implements ";
            parts.add(buildTypeList(keyword, interfaces, recursor));
        }
        if (!permits.isEmpty()) {
            parts.add(buildTypeList("permits ", permits, recursor));
        }
        return new Doc.Group(new Doc.Concat(parts));
    }

    private Doc buildTypeList(final String keyword, final List<? extends Tree> types, final Recursor recursor) {
        final var typesInterior = new Doc.Concat(Doc.intersperse(List.of(new Doc.Text(","), Doc.LINE), types.stream().map(recursor::scan)));
        return Doc.continuationIndent(new Doc.Concat(List.of(Doc.LINE, new Doc.Text(keyword), new Doc.Group(new Doc.Indent(typesInterior)))));
    }

    /**
     * Renders an implicit (unnamed) class — a Java 25 compact source file with no
     * explicit class declaration. The members are emitted at top level (no header, no
     * braces, no indentation wrapper), separated by blank lines, in declaration order.
     */
    Doc renderImplicit(final ClassTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var memberStream = node.getMembers()
                .stream()
                .filter(m -> m instanceof VariableTree || m instanceof MethodTree || m instanceof ClassTree || m instanceof BlockTree);
        final var members = memberStream.flatMap(m -> Optional.ofNullable(renderImplicitMember(m, recursor, attacher)).stream())
                .toList();
        if (members.isEmpty()) {
            return new Doc.Text("");
        }
        return new Doc.Concat(Doc.intersperse(new Doc.Concat(List.of(Doc.HARD_LINE, Doc.HARD_LINE)), members.stream()));
    }

    @Nullable private static Doc renderImplicitMember(final Tree member, final Recursor recursor, final LeadingCommentAttacher attacher) {
        if (member instanceof BlockTree bt) {
            return attacher.attach(bt, InitBlockRenderer.render(bt, recursor, attacher));
        }
        return recursor.scan(member);
    }
}
