package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ImportTree;

import java.util.List;
import java.util.stream.Stream;

final class ImportSectionRenderer {

    static Stream<Doc> buildImportSection(
            final boolean hasPackage,
            final List<? extends ImportTree> imports) {
        if (imports.isEmpty()) {
            return Stream.empty();
        }
        final var statics = imports.stream()
            .filter(ImportTree::isStatic)
            .map(i -> i.getQualifiedIdentifier().toString())
            .sorted()
            .toList();
        final var nonStatics = imports.stream()
            .filter(i -> !i.isStatic())
            .map(i -> i.getQualifiedIdentifier().toString())
            .sorted()
            .toList();
        return Stream.of(
            hasPackage ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            statics.stream().flatMap(n -> Stream.<Doc>of(new Doc.Text("import static " + n + ";"), new Doc.HardLine())),
            !statics.isEmpty() && !nonStatics.isEmpty() ? Stream.<Doc>of(new Doc.HardLine()) : Stream.<Doc>empty(),
            nonStatics.stream().flatMap(n -> Stream.<Doc>of(new Doc.Text("import " + n + ";"), new Doc.HardLine())),
            Stream.<Doc>of(new Doc.HardLine())
        ).flatMap(s -> s);
    }

    private ImportSectionRenderer() {}
}
