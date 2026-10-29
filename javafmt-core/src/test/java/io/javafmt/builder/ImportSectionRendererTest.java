package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import io.javafmt.printer.Printer;
import org.junit.jupiter.api.Test;

class ImportSectionRendererTest {

    private static final int WIDTH = 150;

    private static String format(final String source) {
        return new Printer(WIDTH).print(DocBuilder.build(JavaParser.parseUnit(source))).stripTrailing();
    }

    @Test
    void unsortedNonStaticImports_areSorted() {
        assertThat(format("""
                import java.util.List;
                import java.io.File;
                class Foo {}"""))
                .isEqualTo("import java.io.File;\nimport java.util.List;\n\nclass Foo {}");
    }

    @Test
    void mixedImports_staticFirstThenBlankLineThenNonStatic() {
        assertThat(format("""
                import java.util.List;
                import static java.util.Collections.emptyList;
                class Foo {}"""))
                .isEqualTo("import static java.util.Collections.emptyList;\n\nimport java.util.List;\n\nclass Foo {}");
    }

    @Test
    void alreadySortedImports_areIdempotent() {
        final var source = "import java.io.File;\nimport java.util.List;\nclass Foo {}";
        assertThat(format(format(source))).isEqualTo(format(source));
    }

    @Test
    void importsAfterPackage_haveBlankLineSeparator() {
        assertThat(format("""
                package com.example;
                import java.util.List;
                class Foo {}"""))
                .isEqualTo("package com.example;\n\nimport java.util.List;\n\nclass Foo {}");
    }

    @Test
    void moduleImport_renderedWithModuleKeyword() {
        // Java 25: import module java.base; is a module import declaration
        assertThat(format("""
                import module java.base;
                class Foo {}"""))
                .isEqualTo("import module java.base;\n\nclass Foo {}");
    }

    @Test
    void moduleImports_placedBeforeStaticAndNonStatic() {
        assertThat(format("""
                import java.util.List;
                import static java.util.Collections.emptyList;
                import module java.base;
                class Foo {}"""))
                .isEqualTo("import module java.base;\n\nimport static java.util.Collections.emptyList;\n\nimport java.util.List;\n\nclass Foo {}");
    }

    @Test
    void moduleImports_sortedAlphabetically() {
        assertThat(format("""
                import module java.sql;
                import module java.base;
                class Foo {}"""))
                .isEqualTo("import module java.base;\nimport module java.sql;\n\nclass Foo {}");
    }
}
