package io.javafmt.daemon;

import io.javafmt.Position;
import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DaemonHandlerTest {

    private static final String STAR_IMPORT = "import java.util.*;\n\nclass Order {\n    List<String> lines;\n}\n";

    @Test
    void echoesTheRequestId() {
        assertThat(
                        DaemonHandler.handle(DaemonFixtures.request(42, "class Foo {}"))
                                .getId())
                .isEqualTo(42);
    }

    @Test
    void formatsValidJava() {
        final var reply = DaemonHandler.handle(DaemonFixtures.request(1, "class Foo { }"));
        assertThat(reply.getOutput()).isEqualTo("class Foo {}\n");
        assertThat(reply.getDiagnosticsList()).isEmpty();
    }

    @Test
    void returnsTheOriginalSourceWithAParseErrorWhenItCannotParse() {
        final var source = "class Foo {{{";
        final var reply = DaemonHandler.handle(DaemonFixtures.request(2, source));
        assertThat(reply.getOutput()).isEqualTo(source);
        assertThat(reply.getDiagnosticsList()).first().satisfies(d -> {
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
        assertThat(DaemonHandler.handle(DaemonFixtures.request(3, source)).getDiagnosticsList())
                .extracting(Diagnostic::getSeverity, Diagnostic::getKind)
                .contains(tuple(Severity.SEVERITY_WARNING, Kind.KIND_WARNING));
    }

    @Test
    void withoutAClasspathTheRulesThatNeedOneDoNotRun() {
        assertThat(DaemonHandler.handle(DaemonFixtures.request(4, STAR_IMPORT)).getOutput())
                .contains("import java.util.*;");
    }

    @Test
    void aClasspathTurnsOnTheRulesThatNeedIt() {
        final var reply = DaemonHandler.handle(DaemonFixtures.request(5, STAR_IMPORT).toBuilder()
                .setClasspath("")
                .build());
        assertThat(reply.getOutput())
                .contains("import java.util.List;")
                .doesNotContain("java.util.*");
    }

    @Test
    void theProjectFileForTheRequestsPathApplies(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=ExplodeStarImports\n");
        final var reply = DaemonHandler.handle(DaemonFixtures.request(6, STAR_IMPORT).toBuilder()
                .setClasspath("")
                .setPath(dir.resolve("src/Order.java").toString())
                .build());
        assertThat(reply.getOutput()).contains("import java.util.*;");
    }

    @Test
    void anInvalidProjectFileIsAConfigurationError(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=FinalParameters\n");
        final var reply = DaemonHandler.handle(DaemonFixtures.request(7, STAR_IMPORT).toBuilder()
                .setPath(dir.resolve("Order.java").toString())
                .build());
        assertThat(reply.getOutput()).isEqualTo(STAR_IMPORT);
        assertThat(reply.getDiagnosticsList()).singleElement().satisfies(d -> {
            assertThat(d.getSeverity()).isEqualTo(Severity.SEVERITY_ERROR);
            assertThat(d.getKind()).isEqualTo(Kind.KIND_CONFIG);
            assertThat(d.getMessage()).contains("FinalParameters");
        });
        assertThat(DaemonFixtures.violations(reply)).isEmpty();
    }

    @Test
    void aPathThatIsNotAPathIsAnErrorInTheRequest() {
        final var reply = DaemonHandler.handle(DaemonFixtures.request(8, STAR_IMPORT).toBuilder()
                .setPath("bad\0path")
                .build());
        assertThat(reply.getOutput()).isEqualTo(STAR_IMPORT);
        assertThat(reply.getDiagnosticsList())
                .extracting(Diagnostic::getKind)
                .containsExactly(Kind.KIND_CLI);
    }

    @ParameterizedTest
    @ValueSource(strings = {"class Foo { }", "class Foo {{{", "", "class Foo { void m(int x) { switch (x) { case 1: m(1); case 2: break; } } }"})
    void everyReplyKeepsTheProtocolRules(final String source) {
        assertThat(DaemonFixtures.violations(DaemonHandler.handle(DaemonFixtures.request(1, source))))
                .isEmpty();
    }

    @Test
    void aFailureCarriesTheGivenKindAsAnError() {
        assertThat(
                        DaemonHandler.failure(7, "src", Kind.KIND_CLI, "request has no source")
                                .getDiagnosticsList())
                .containsExactly(Diagnostic.newBuilder()
                        .setSeverity(Severity.SEVERITY_ERROR)
                        .setKind(Kind.KIND_CLI)
                        .setMessage("request has no source")
                        .build());
    }

    static Stream<Arguments> coreDiagnostics() {
        final var at = new Position.At(3, 5, 0);
        return Stream.of(
                arguments(new io.javafmt.Diagnostic.ParseError("m", at), Kind.KIND_PARSE, Severity.SEVERITY_ERROR),
                arguments(new io.javafmt.Diagnostic.LintError("m", at), Kind.KIND_LINT, Severity.SEVERITY_ERROR),
                arguments(new io.javafmt.Diagnostic.ConfigError("m", at), Kind.KIND_CONFIG, Severity.SEVERITY_ERROR),
                arguments(new io.javafmt.Diagnostic.InternalError("m", at), Kind.KIND_INTERNAL, Severity.SEVERITY_ERROR),
                arguments(new io.javafmt.Diagnostic.Warning("m", at), Kind.KIND_WARNING, Severity.SEVERITY_WARNING));
    }

    @ParameterizedTest
    @MethodSource("coreDiagnostics")
    void eachCoreDiagnosticMapsToItsKindSeverityAndPosition(final io.javafmt.Diagnostic core, final Kind kind, final Severity severity) {
        assertThat(DaemonHandler.diagnostic(core))
                .isEqualTo(Diagnostic.newBuilder()
                        .setSeverity(severity)
                        .setKind(kind)
                        .setMessage("m")
                        .setLine(3)
                        .setColumn(5)
                        .build());
    }
}
