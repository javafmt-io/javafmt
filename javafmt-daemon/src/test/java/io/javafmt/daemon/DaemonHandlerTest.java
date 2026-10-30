package io.javafmt.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DaemonHandlerTest {

    @Test
    void echoesTheRequestId() {
        assertThat(DaemonHandler.handle(42, "class Foo {}").getId()).isEqualTo(42);
    }

    @Test
    void formatsValidJava() {
        final var reply = DaemonHandler.handle(1, "class Foo { }");
        assertThat(reply.getOutput()).isEqualTo("class Foo {}\n");
        assertThat(reply.getDiagnosticsList()).isEmpty();
    }

    @Test
    void returnsTheOriginalSourceWithAParseErrorWhenItCannotParse() {
        final var source = "class Foo {{{";
        final var reply = DaemonHandler.handle(2, source);
        assertThat(reply.getOutput()).isEqualTo(source);
        assertThat(reply.getDiagnosticsList())
                .first()
                .satisfies(d -> {
                    assertThat(d.getSeverity()).isEqualTo(Severity.SEVERITY_ERROR);
                    assertThat(d.getKind()).isEqualTo(Kind.KIND_PARSE);
                    assertThat(d.getLine()).isPositive();
                    assertThat(d.getColumn()).isPositive();
                });
    }

    @Test
    void mapsALintWarningToWarningSeverityAndKind() {
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
        assertThat(DaemonHandler.handle(3, source).getDiagnosticsList())
                .extracting(Diagnostic::getSeverity, Diagnostic::getKind)
                .contains(tuple(Severity.SEVERITY_WARNING, Kind.KIND_WARNING));
    }

    @ParameterizedTest
    @ValueSource(strings = {"class Foo { }", "class Foo {{{", "", "class Foo { void m(int x) { switch (x) { case 1: m(1); case 2: break; } } }"})
    void everyReplyKeepsTheProtocolRules(final String source) {
        assertThat(DaemonFixtures.violations(DaemonHandler.handle(1, source))).isEmpty();
    }

    @Test
    void aFailureCarriesTheGivenKindAsAnError() {
        assertThat(DaemonHandler.failure(7, "src", Kind.KIND_CLI, "request has no source").getDiagnosticsList())
                .containsExactly(
                        Diagnostic.newBuilder()
                                .setSeverity(Severity.SEVERITY_ERROR)
                                .setKind(Kind.KIND_CLI)
                                .setMessage("request has no source")
                                .build());
    }
}
