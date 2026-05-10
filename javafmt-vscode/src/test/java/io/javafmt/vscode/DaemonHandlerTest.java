package io.javafmt.vscode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DaemonHandlerTest {

    @Test
    void echoesRequestId() {
        final var req = new FormatRequest("req-42", "class Foo {}");
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.id()).isEqualTo("req-42");
    }

    @Test
    void formatsValidJava() {
        final var req = new FormatRequest("id1", "class Foo { }");
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.output()).contains("class Foo");
        assertThat(resp.diagnostics()).isEmpty();
    }

    @Test
    void returnsOriginalSourceOnParseError() {
        final var source = "class Foo {{{";
        final var req = new FormatRequest("id2", source);
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.output()).isEqualTo(source);
        assertThat(resp.diagnostics()).isNotEmpty();
        assertThat(resp.diagnostics().get(0).severity()).isEqualTo("error");
    }

    @Test
    void absentConfigPreservesDeclarationOrder() {
        // method declared before field — without reorderMembers, declaration order is kept
        final var source = """
            class Foo {
                public void m() {}
                int field;
            }
            """;
        final var req = new FormatRequest("id4", source);
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.output().indexOf("void m()"))
            .isLessThan(resp.output().indexOf("int field"));
    }

    @Test
    void configReorderMembersPassedToFormatter() {
        // method declared before field — with reorderMembers, field (INSTANCE_FIELD) moves above method (PUBLIC_METHOD)
        final var source = """
            class Foo {
                public void m() {}
                int field;
            }
            """;
        final var req = new FormatRequest("id5", source, new ConfigDto(true));
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.output().indexOf("int field"))
            .isLessThan(resp.output().indexOf("void m()"));
    }

    @Test
    void mapsLintWarningToWarningSeverity() {
        // FallThrough triggers a warning diagnostic
        final var source = """
            class Foo {
                void m(int x) {
                    switch (x) {
                        case 1:
                            System.out.println();
                        case 2:
                            break;
                    }
                }
            }
            """;
        final var req = new FormatRequest("id3", source);
        final var resp = DaemonHandler.handle(req);
        assertThat(resp.diagnostics()).anyMatch(d -> d.severity().equals("warning"));
    }
}
