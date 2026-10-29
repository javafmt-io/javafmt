package io.javafmt.builder;

import com.sun.source.tree.DirectiveTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.UsesTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Renders a module declaration. Nothing here reorders anything: the directives and every
 * {@code to} and {@code with} list stay in the order the author wrote them, because
 * {@code ServiceLoader} honours declaration order for module-declared providers — sorting a
 * {@code provides ... with} list changes what {@code findFirst()} returns. A blank line separates
 * runs of different directive kinds, which is what google-java-format does.
 */
@UtilityClass
final class ModuleRenderer {

    Doc render(final ModuleTree node, final LeadingCommentAttacher attacher, final Recursor recursor) {
        final var header = headerText(node);
        final var directives = node.getDirectives();
        final var body = directives.isEmpty()
                ? new Doc.Concat(List.of(new Doc.Text(header), new Doc.Text(" {}")))
                : Doc.concatOf(Stream.of(new Doc.Text(header), new Doc.Text(" {")), bodyStream(directives, attacher), Doc.closeBrace());
        return ModifierRenderer.prependOwnLineAnnotations(node.getAnnotations(), body, recursor);
    }

    private String headerText(final ModuleTree node) {
        final var prefix = node.getModuleType() == ModuleTree.ModuleKind.OPEN ? "open module " : "module ";
        return prefix + node.getName();
    }

    private Stream<Doc> bodyStream(final List<? extends DirectiveTree> directives, final LeadingCommentAttacher attacher) {
        return IntStream.range(0, directives.size())
                .mapToObj(i -> {
                    final var d = directives.get(i);
                    final var doc = attacher.attach(d, renderDirective(d));
                    final var indented = Doc.indentedLine(doc);
                    if (i > 0 && directives.get(i - 1).getKind() != d.getKind()) {
                        return new Doc.Concat(List.of(Doc.HARD_LINE, indented));
                    }
                    return indented;
                });
    }

    private Doc renderDirective(final DirectiveTree directive) {
        return switch (directive) {
            case RequiresTree r -> renderRequires(r);
            case ExportsTree e -> renderExports(e);
            case OpensTree o -> renderOpens(o);
            case UsesTree u -> renderUses(u);
            case ProvidesTree p -> renderProvides(p);
            default -> throw new IllegalStateException("unexpected module directive kind: " + directive.getKind());
        };
    }

    private Doc renderRequires(final RequiresTree node) {
        final var modifiers = new StringBuilder("requires ");
        if (node.isStatic()) {
            modifiers.append("static ");
        }
        if (node.isTransitive()) {
            modifiers.append("transitive ");
        }
        return new Doc.Text(modifiers.toString() + node.getModuleName() + ";");
    }

    private Doc renderExports(final ExportsTree node) {
        return renderTargeted("exports", node.getPackageName(), node.getModuleNames(), "to");
    }

    private Doc renderOpens(final OpensTree node) {
        return renderTargeted("opens", node.getPackageName(), node.getModuleNames(), "to");
    }

    private Doc renderUses(final UsesTree node) {
        return new Doc.Text("uses " + node.getServiceName() + ";");
    }

    private Doc renderProvides(final ProvidesTree node) {
        return renderTargeted("provides", node.getServiceName(), node.getImplementationNames(), "with");
    }

    private Doc renderTargeted(
            final String keyword,
            final ExpressionTree subject,
            final List<? extends ExpressionTree> targets,
            final String connector) {
        if (targets == null || targets.isEmpty()) {
            return new Doc.Text(keyword + " " + subject + ";");
        }
        final var inSourceOrder = targets.stream().map(ExpressionTree::toString).<Doc>map(Doc.Text::new);
        final var interior = new Doc.Concat(Doc.intersperse(List.of(new Doc.Text(","), Doc.LINE), inSourceOrder));
        final var continuation = Doc.continuationIndent(new Doc.Concat(List.of(Doc.LINE, interior)));
        return new Doc.Group(new Doc.Concat(List.of(new Doc.Text(keyword + " " + subject + " " + connector), continuation, new Doc.Text(";"))));
    }
}
