package io.javafmt.builder;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.TryTree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class TryRenderer {

    Doc render(final TryTree node, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var header = buildHeader(node, recursor);
        final var tryStmts = BlockRenderer.blockStmts(node.getBlock(), recursor, attacher);
        final var catchStream = node.getCatches().stream().flatMap(c -> catchParts(c, recursor, attacher));
        final var finallyBlock = node.getFinallyBlock();
        final var finallyStream = finallyBlock == null
                ? Stream.<Doc>empty()
                : Stream.concat(
                        Stream.<Doc>of(Doc.HARD_LINE, new Doc.Text("} finally {")),
                        BlockRenderer.blockStmts(finallyBlock, recursor, attacher)
                                .stream()
                                .map(Doc::indentedLine));
        return Doc.concatOf(BlockRenderer.blockParts(header, tryStmts), catchStream, finallyStream, Doc.closeBrace());
    }

    private Doc buildHeader(final TryTree node, final Recursor recursor) {
        final var resources = node.getResources();
        if (resources == null || resources.isEmpty()) {
            return new Doc.Text("try");
        }
        final var rendered = resources.stream().map(r -> BlockRenderer.stripTrailingSemicolonDoc(recursor.scan(r)));
        return new Doc.Concat(List.of(new Doc.Text("try "), WrappableList.of("(", ";", ")", rendered)));
    }

    /**
     * The {@code catch (Type name)} clause on its own, without the braces around it. The two
     * callers wrap it differently and both are right: here the clause is one link in a K&R
     * chain, so it is preceded by the closing brace of the block before it and its own block
     * is closed by whatever comes next ({@code } catch (E e) {}); {@link
     * DocBuilder#visitCatch} renders a catch with no such neighbours and so closes its own
     * block. Only the clause text itself has to stay identical between them.
     */
    Doc catchClause(final CatchTree c, final Recursor recursor) {
        final var param = c.getParameter();
        return new Doc.Concat(
                List.of(
                        new Doc.Text("catch (" + ModifierRenderer.prefixOf(param.getModifiers(), recursor)),
                        recursor.scan(param.getType()),
                        new Doc.Text(" " + Names.of(param) + ")")));
    }

    private Stream<Doc> catchParts(final CatchTree c, final Recursor recursor, final LeadingCommentAttacher attacher) {
        final var header = new Doc.Concat(List.of(new Doc.Text("} "), catchClause(c, recursor)));
        return Stream.concat(Stream.of(Doc.HARD_LINE), BlockRenderer.blockParts(header, BlockRenderer.blockStmts(c.getBlock(), recursor, attacher)));
    }
}
