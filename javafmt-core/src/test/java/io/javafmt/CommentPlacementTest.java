package io.javafmt;

import io.javafmt.parser.SourceToken;
import io.javafmt.parser.TokenScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommentPlacementTest {

    private static final int LINE_WIDTH = 150;

    static Stream<Arguments> gaps() throws URISyntaxException, IOException {
        final var root = Paths.get(Objects.requireNonNull(CommentPlacementTest.class.getClassLoader().getResource("comment-placement")).toURI());
        try (final var listing = Files.list(root)) {
            return listing.sorted().toList().stream().flatMap(CommentPlacementTest::gapsIn);
        }
    }

    private static Stream<Arguments> gapsIn(final Path file) {
        final var source = read(file);
        final var tokens = TokenScanner.scan(source);
        final var name = file.getFileName().toString().replace(".java", "");
        return IntStream.range(0, tokens.size() - 1)
                .boxed()
                .flatMap(
                        index -> Arrays.stream(Probe.values())
                                .map(
                                        probe -> Arguments.of(
                                                "%s %s between '%s' and '%s' (token %d)".formatted(
                                                        name, probe.text, tokens.get(index).text(), tokens.get(index + 1).text(), index),
                                                source, index, probe)));
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gaps")
    void commentStaysBetweenTheSameTwoTokens(final String name, final String source, final int index, final Probe probe) {
        final var tokens = TokenScanner.scan(source);
        final var at = tokens.get(index).end();
        final var input = source.substring(0, at) + probe.inserted + source.substring(at);
        final var result = Javafmt.formatWithResult(input, JavafmtConfig.defaults().withVerifyOutput(true));
        final var internalErrors = result.diagnostics().stream().filter(d -> d instanceof Diagnostic.InternalError).map(Diagnostic::message).toList();
        assertThat(internalErrors).as("formatting %s", name).isEmpty();
        final var output = result.output();
        final var scanned = TokenScanner.scan(output);
        final var outputTokens = IntStream.range(0, scanned.size())
                .filter(i -> !scanned.get(i).text().equals(",") || i + 1 == scanned.size() || !scanned.get(i + 1).text().equals("}"))
                .mapToObj(scanned::get)
                .toList();
        assertThat(texts(outputTokens)).as("tokens of %s", name).containsExactlyElementsOf(texts(tokens));
        final var probeAt = output.indexOf(probe.text);
        assertThat(output.indexOf(probe.text, probeAt + 1)).as("the probe appears once in%n%s", output).isEqualTo(-1);
        final var moved = (int) outputTokens.stream().filter(token -> token.end() <= probeAt).count() - (index + 1);
        assertThat(moved == 0 || AllowedMove.allows(probe, tokens.get(index + 1).text(), moved))
                .as("%s moved %d tokens in%n%s", name, moved, output)
                .isTrue();
        assertThat(output.lines().filter(line -> line.length() > LINE_WIDTH).toList()).as("lines wider than %d in%n%s", LINE_WIDTH, output).isEmpty();
        final var lines = output.lines().toList();
        final var probeLine = IntStream.range(0, lines.size()).filter(i -> lines.get(i).contains(probe.text)).findFirst().orElseThrow();
        if (lines.get(probeLine).strip().startsWith(probe.text) && probeLine + 1 < lines.size()) {
            assertThat(indentOf(lines.get(probeLine)))
                    .as("indentation of the comment's own line in%n%s", output)
                    .isEqualTo(indentOf(lines.get(probeLine + 1)));
        }
        assertThat(Javafmt.formatWithResult(output, JavafmtConfig.defaults().withVerifyOutput(true)).output())
                .as("formatting %s a second time", name)
                .isEqualTo(output);
    }

    private static int indentOf(final String line) {
        return line.length() - line.stripLeading().length();
    }

    private static List<String> texts(final List<SourceToken> tokens) {
        return tokens.stream().map(SourceToken::text).toList();
    }

    enum Probe {

        BLOCK(" /* probe */ ", "/* probe */"),
        LINE(" // probe\n", "// probe");

        private final String inserted;

        private final String text;

        Probe(final String inserted, final String text) {
            this.inserted = inserted;
            this.text = text;
        }
    }

    enum AllowedMove {

        COMMA_MOVES_BEFORE_THE_COMMENT(",", false),
        SEMICOLON_MOVES_BEFORE_A_LINE_COMMENT(";", true),
        BRACE_MOVES_UP_BEFORE_A_LINE_COMMENT("{", true);

        private final String token;

        private final boolean lineCommentsOnly;

        AllowedMove(final String token, final boolean lineCommentsOnly) {
            this.token = token;
            this.lineCommentsOnly = lineCommentsOnly;
        }

        static boolean allows(final Probe probe, final String next, final int moved) {
            return moved == 1 && Arrays.stream(values()).anyMatch(move -> move.token.equals(next) && (probe == Probe.LINE || !move.lineCommentsOnly));
        }
    }
}
