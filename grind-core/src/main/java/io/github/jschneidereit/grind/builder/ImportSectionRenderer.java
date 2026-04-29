package io.github.jschneidereit.grind.builder;

import com.sun.source.tree.ImportTree;

import io.github.jschneidereit.grind.parser.ParsedUnit;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import io.github.jschneidereit.grind.doc.Doc;

final class ImportSectionRenderer {

    static Stream<Doc> buildImportSection(
            final boolean hasPackage,
            final List<? extends ImportTree> imports,
            final ParsedUnit unit) {
        if (imports.isEmpty()) {
            return Stream.empty();
        }
        final var statics = imports.stream()
            .filter(ImportTree::isStatic)
            .sorted(Comparator.comparing(i -> i.getQualifiedIdentifier().toString()))
            .toList();
        final var nonStatics = imports.stream()
            .filter(i -> !i.isStatic())
            .sorted(Comparator.comparing(i -> i.getQualifiedIdentifier().toString()))
            .toList();
        return Stream.of(
            hasPackage ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            statics.stream().flatMap(i -> renderImport(i, "import static ", unit)),
            !statics.isEmpty() && !nonStatics.isEmpty() ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            nonStatics.stream().flatMap(i -> renderImport(i, "import ", unit)),
            Stream.<Doc>of(new Doc.HardLine())
        ).flatMap(s -> s);
    }

    private static Stream<Doc> renderImport(final ImportTree imp, final String prefix, final ParsedUnit unit) {
        final var leading = unit.leadingOf(imp);
        final var trailing = unit.trailingOf(imp);
        final var lineDoc = new Doc.Text(prefix + imp.getQualifiedIdentifier() + ";");
        final var withLeading = leading.isEmpty()
            ? lineDoc
            : CommentDocs.prepend(leading, lineDoc);
        final var withTrailing = trailing.isEmpty()
            ? withLeading
            : CommentDocs.appendTrailing(withLeading, trailing);
        return Stream.of(withTrailing, new Doc.HardLine());
    }

    private ImportSectionRenderer() {}
}
