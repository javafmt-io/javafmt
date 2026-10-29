package io.javafmt.builder;

import com.sun.source.tree.ImportTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.ParsedUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
final class ImportSectionRenderer {

    /** Java 25 module imports ({@code import module M;}) come first, then statics, then non-statics. */
    Stream<Doc> buildImportSection(final boolean hasPackage, final List<? extends ImportTree> imports, final ParsedUnit unit) {
        if (imports.isEmpty()) {
            return Stream.empty();
        }
        final var modules = imports.stream()
                .filter(ImportTree::isModule)
                .sorted(Comparator.comparing(i -> i.getQualifiedIdentifier().toString()))
                .toList();
        final var statics = imports.stream()
                .filter(i -> !i.isModule() && i.isStatic())
                .sorted(Comparator.comparing(i -> i.getQualifiedIdentifier().toString()))
                .toList();
        final var nonStatics = imports.stream()
                .filter(i -> !i.isModule() && !i.isStatic())
                .sorted(Comparator.comparing(i -> i.getQualifiedIdentifier().toString()))
                .toList();
        return Stream.of(
                hasPackage ? Stream.<Doc>of(Doc.HARD_LINE) : Stream.<Doc>empty(),
                modules.stream().flatMap(i -> renderImport(i, "import module ", unit)),
                !modules.isEmpty() && (!statics.isEmpty() || !nonStatics.isEmpty()) ? Stream.<Doc>of(Doc.HARD_LINE) : Stream.<Doc>empty(),
                statics.stream().flatMap(i -> renderImport(i, "import static ", unit)),
                !statics.isEmpty() && !nonStatics.isEmpty() ? Stream.<Doc>of(Doc.HARD_LINE) : Stream.<Doc>empty(),
                nonStatics.stream().flatMap(i -> renderImport(i, "import ", unit)),
                Stream.<Doc>of(Doc.HARD_LINE))
                .flatMap(Function.identity());
    }

    private Stream<Doc> renderImport(final ImportTree imp, final String prefix, final ParsedUnit unit) {
        final var leading = unit.leadingOf(imp);
        final var trailing = unit.trailingOf(imp);
        final var lineDoc = new Doc.Text(prefix + imp.getQualifiedIdentifier() + ";");
        final var withLeading = leading.isEmpty() ? lineDoc : CommentDocs.prepend(leading, lineDoc);
        final var withTrailing = trailing.isEmpty() ? withLeading : CommentDocs.appendTrailing(withLeading, trailing);
        return Stream.of(withTrailing, Doc.HARD_LINE);
    }
}
