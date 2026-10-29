package io.javafmt.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Javafmt;
import org.junit.jupiter.api.Test;

class ModuleRendererTest {

    @Test
    void emptyModule_rendersOnOneLine() {
        assertThat(Javafmt.format("module foo.bar {}").stripTrailing()).isEqualTo("module foo.bar {}");
    }

    @Test
    void openModule_keepsOpenKeyword() {
        assertThat(Javafmt.format("open module foo.bar {}").stripTrailing()).isEqualTo("open module foo.bar {}");
    }

    @Test
    void singleRequires_isIndentedInsideBody() {
        assertThat(Javafmt.format("module foo.bar { requires java.base; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    requires java.base;\n}");
    }

    @Test
    void requiresStatic_keepsStaticModifier() {
        assertThat(Javafmt.format("module foo.bar { requires static java.compiler; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    requires static java.compiler;\n}");
    }

    @Test
    void requiresTransitive_keepsTransitiveModifier() {
        assertThat(Javafmt.format("module foo.bar { requires transitive java.desktop; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    requires transitive java.desktop;\n}");
    }

    @Test
    void exportsWithoutTo_singleLine() {
        assertThat(Javafmt.format("module foo.bar { exports a.b; }").stripTrailing()).isEqualTo("module foo.bar {\n    exports a.b;\n}");
    }

    @Test
    void exportsToMultiple_singleLineWhenFits() {
        assertThat(Javafmt.format("module foo.bar { exports a.b to m1, m2; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    exports a.b to m1, m2;\n}");
    }

    @Test
    void exportsTo_targetsKeepSourceOrder() {
        assertThat(Javafmt.format("module foo.bar { exports a.b to zeta, alpha, beta; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    exports a.b to zeta, alpha, beta;\n}");
    }

    @Test
    void opensTo_targetsKeepSourceOrder() {
        assertThat(Javafmt.format("module foo.bar { opens a.b to zeta, alpha; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    opens a.b to zeta, alpha;\n}");
    }

    @Test
    void providesWith_implementationsKeepSourceOrder() {
        // ServiceLoader honours declaration order for module-declared providers, so sorting the
        // `with` list can change what findFirst() returns.
        assertThat(Javafmt.format("module foo.bar { provides a.S with b.Zeta, b.Alpha; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    provides a.S with b.Zeta, b.Alpha;\n}");
    }

    @Test
    void uses_singleLine() {
        assertThat(Javafmt.format("module foo.bar { uses a.Service; }").stripTrailing()).isEqualTo("module foo.bar {\n    uses a.Service;\n}");
    }

    @Test
    void provides_singleLine() {
        assertThat(Javafmt.format("module foo.bar { provides a.Service with a.Impl; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    provides a.Service with a.Impl;\n}");
    }

    @Test
    void provides_implsKeepSourceOrder() {
        assertThat(Javafmt.format("module foo.bar { provides a.Service with z.Z, a.A, m.M; }").stripTrailing())
                .isEqualTo("module foo.bar {\n    provides a.Service with z.Z, a.A, m.M;\n}");
    }

    @Test
    void blankLineBetweenGroups() {
        final var source = "module foo.bar { requires java.base; exports a.b; uses a.Service; }";
        assertThat(Javafmt.format(source).stripTrailing())
                .isEqualTo("""
                           module foo.bar {
                               requires java.base;

                               exports a.b;

                               uses a.Service;
                           }""");
    }

    @Test
    void groupsPreserveDeclarationOrderWithin() {
        final var source = "module foo.bar { requires z; requires a; requires m; }";
        assertThat(Javafmt.format(source).stripTrailing())
                .isEqualTo("""
                           module foo.bar {
                               requires z;
                               requires a;
                               requires m;
                           }""");
    }

    @Test
    void exportsTo_longList_breaksWithEightSpaceContinuation() {
        // 4-space indent + "exports my.very.detailed.api to " (33) + many targets
        // pushes well past 150 chars when flat, so the group must break.
        final var source = "module foo.bar { exports my.very.detailed.api to "
            + "moduleAlpha01, moduleBeta02, moduleGamma03, moduleDelta04, moduleEpsilon05, "
            + "moduleZeta06, moduleEta07, moduleTheta08, moduleIota09, moduleKappa10; }";
        final var formatted = Javafmt.format(source);
        // targets land at column 12 = 4 (module body) + 8 (continuation past `exports`),
        // matching the method-chain rule from docs/rules.md.
        assertThat(formatted).contains("\n            moduleAlpha01");
        // verify no line exceeds 150 chars
        formatted.lines().forEach(line -> assertThat(line.length()).isLessThanOrEqualTo(150));
    }
}
