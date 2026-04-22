package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.StatementTree;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class BlockRenderer {

    private BlockRenderer() {}

    static List<Doc> blockStmts(final StatementTree stmt, final Recursor recursor) {
        if (stmt instanceof BlockTree block) {
            return block.getStatements().stream()
                .flatMap(s -> Optional.ofNullable(recursor.scan(s)).stream())
                .toList();
        }
        return Optional.ofNullable(recursor.scan(stmt)).map(List::of).orElse(List.of());
    }

    static Doc buildBlock(final String header, final List<Doc> stmts) {
        if (stmts.isEmpty()) {
            return new Doc.Text(header + " {}");
        }
        return new Doc.Concat(Stream.concat(
            blockParts(header, stmts),
            Stream.<Doc>of(new Doc.HardLine(), new Doc.Text("}"))
        ));
    }

    static Stream<Doc> blockParts(final String header, final List<Doc> stmts) {
        return Stream.concat(
            Stream.<Doc>of(new Doc.Text(header + " {")),
            stmts.stream()
                .<Doc>map(s -> new Doc.Indent(new Doc.Concat(List.of(new Doc.HardLine(), s))))
        );
    }

    static String stripTrailingSemicolon(final String s) {
        return s.endsWith(";") ? s.substring(0, s.length() - 1) : s;
    }
}
