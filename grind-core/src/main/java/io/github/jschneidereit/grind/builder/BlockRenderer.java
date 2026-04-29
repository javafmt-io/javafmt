package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;

import io.github.jschneidereit.grind.parser.CommentToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import io.github.jschneidereit.grind.doc.Doc;

final class BlockRenderer {

    static List<Doc> blockStmts(final StatementTree stmt, final Recursor recursor) {
        if (stmt instanceof BlockTree block) {
            return block.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
        }
        recursor.emitWarning("added braces around single-statement body", stmt);
        return Optional.ofNullable(recursor.scan(stmt)).map(List::of).orElse(List.of());
    }

    static Doc buildBlock(final String header, final List<Doc> stmts, final List<CommentToken> interior) {
        return buildBlock(new Doc.Text(header), stmts, interior);
    }

    static Doc buildBlock(final Doc header, final List<Doc> stmts, final List<CommentToken> interior) {
        if (stmts.isEmpty() && interior.isEmpty()) {
            return new Doc.Concat(List.of(header, new Doc.Text(" {}")));
        }
        final var interiorDocs = interior.stream().<Doc>map(CommentDocs::renderComment).toList();
        final var all = Stream.concat(interiorDocs.stream(), stmts.stream()).toList();
        return new Doc.Concat(Stream.concat(
            blockParts(header, all),
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    static Doc buildBlock(final List<Doc> stmts, final List<CommentToken> interior) {
        if (stmts.isEmpty() && interior.isEmpty()) {
            return new Doc.Text("{}");
        }
        final var interiorDocs = interior.stream().<Doc>map(CommentDocs::renderComment).toList();
        final var all = Stream.concat(interiorDocs.stream(), stmts.stream()).toList();
        return new Doc.Concat(Stream.concat(
            Stream.concat(
                Stream.<Doc>of(new Doc.Text("{")),
                all.stream()
                    .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
            ),
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    static Stream<Doc> blockParts(final String header, final List<Doc> stmts) {
        return blockParts(new Doc.Text(header), stmts);
    }

    static Stream<Doc> blockParts(final Doc header, final List<Doc> stmts) {
        return Stream.concat(
            Stream.<Doc>of(header, new Doc.Text(" {")),
            stmts.stream()
                .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
    }

    static Doc stripTrailingSemicolonDoc(final Doc doc) {
        if (doc instanceof Doc.Text(var value) && value.endsWith(";")) {
            return new Doc.Text(value.substring(0, value.length() - 1));
        }
        if (doc instanceof Doc.Concat(var parts) && !parts.isEmpty()) {
            final var last = parts.get(parts.size() - 1);
            final var stripped = stripTrailingSemicolonDoc(last);
            if (stripped != last) {
                final var newParts = new ArrayList<>(parts);
                newParts.set(newParts.size() - 1, stripped);
                return new Doc.Concat(newParts);
            }
        }
        return doc;
    }

    private BlockRenderer() {}
}
