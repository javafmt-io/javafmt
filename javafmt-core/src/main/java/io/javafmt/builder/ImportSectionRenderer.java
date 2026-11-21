package io.javafmt.builder;

import com.sun.source.tree.ImportTree;
import io.javafmt.doc.Doc;
import io.javafmt.parser.ParsedUnit;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
final class ImportSectionRenderer {

    /** Java 25 module imports ({@code import module M;}) come first, then third-party, then {@code java.*}/{@code javax.*}, then statics. */
    Stream<Doc> buildImportSection(final boolean hasPackage, final List<? extends ImportTree> imports, final ParsedUnit unit) {
        if (imports.isEmpty()) {
            return Stream.empty();
        }
        final var groups = imports.stream()
                .map(NamedImport::of)
                .sorted(Comparator.comparing(NamedImport::name))
                .collect(Collectors.groupingBy(NamedImport::group, TreeMap::new, Collectors.toList()))
                .values()
                .stream()
                .flatMap(group -> Stream.concat(Stream.of(Doc.HARD_LINE), group.stream()
                        .flatMap(i -> renderImport(i, unit))))
                .skip(1);
        return Stream.of(hasPackage ? Stream.<Doc>of(Doc.HARD_LINE) : Stream.<Doc>empty(), groups, Stream.<Doc>of(Doc.HARD_LINE))
                .flatMap(Function.identity());
    }

    private Stream<Doc> renderImport(final NamedImport imp, final ParsedUnit unit) {
        final var leading = unit.leadingOf(imp.tree());
        final var trailing = unit.trailingOf(imp.tree());
        final var lineDoc = new Doc.Text(imp.prefix() + imp.name() + ";");
        final var withLeading = CommentDocs.prepend(leading, lineDoc);
        final var withTrailing = CommentDocs.appendTrailing(withLeading, trailing);
        return Stream.of(withTrailing, Doc.HARD_LINE);
    }

    private record NamedImport(ImportTree tree, String name, String prefix) {

        static NamedImport of(final ImportTree tree) {
            return new NamedImport(tree, tree.getQualifiedIdentifier().toString(), prefixOf(tree));
        }

        private static String prefixOf(final ImportTree tree) {
            if (tree.isModule()) {
                return "import module ";
            }
            return tree.isStatic() ? "import static " : "import ";
        }

        ImportGroup group() {
            if (tree.isModule()) {
                return ImportGroup.MODULE;
            }
            if (tree.isStatic()) {
                return ImportGroup.STATIC;
            }
            return name.startsWith("java.") || name.startsWith("javax.") ? ImportGroup.JDK : ImportGroup.THIRD_PARTY;
        }
    }

    private enum ImportGroup { MODULE, THIRD_PARTY, JDK, STATIC }
}
