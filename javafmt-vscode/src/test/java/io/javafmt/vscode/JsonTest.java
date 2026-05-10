package io.javafmt.vscode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonTest {

    @Test
    void deserializesFormatRequestWithConfig() {
        final var json = """
            {"id":"x","source":"class A {}","config":{"reorderMembers":true}}""";
        final var req = Json.parse(json, FormatRequest.class);
        assertThat(req.config()).isNotNull();
        assertThat(req.config().reorderMembers()).isTrue();
    }

    @Test
    void deserializesFormatRequestWithoutConfig() {
        final var json = """
            {"id":"x","source":"class A {}"}""";
        final var req = Json.parse(json, FormatRequest.class);
        assertThat(req.config()).isNull();
    }

    @Test
    void deserializesFormatRequest() {
        final var json = """
            {"id":"abc123","source":"class A {}"}""";
        final var req = Json.parse(json, FormatRequest.class);
        assertThat(req.id()).isEqualTo("abc123");
        assertThat(req.source()).isEqualTo("class A {}");
    }

    @Test
    void serializesFormatResponse() {
        final var resp = new FormatResponse("abc123", "class A {}\n", List.of());
        final var json = Json.serialize(resp);
        assertThat(json).contains("\"id\":\"abc123\"");
        assertThat(json).contains("\"diagnostics\":[]");
    }

    @Test
    void serializesResponseWithDiagnostic() {
        final var diag = new DiagnosticDto("error", "unexpected token", 7, 3);
        final var resp = new FormatResponse("xyz", "class Broken {{{", List.of(diag));
        final var json = Json.serialize(resp);
        assertThat(json).contains("\"severity\":\"error\"");
        assertThat(json).contains("\"line\":7");
        assertThat(json).contains("\"col\":3");
    }
}
