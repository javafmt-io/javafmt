package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jschneidereit.grind.parser.CommentScanner;
import io.github.jschneidereit.grind.parser.CommentToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommentAuditTest {

    private static final String SOURCE = """
        package p;

        import java.util.List;
        import java.util.Map;

        /** class javadoc */
        class Fixture extends /* superclass note */ Parent implements /* iface1 */ A, /* iface2 */ B {
            <T /* type param */, U> void generic(T t, U u) {}

            void throwy() throws /* checked */ java.io.IOException, /* unchecked */ RuntimeException {}

            int x = 1 /* suffix */;

            int y = /* init expr */ 2 + 3;

            record Point(int a, /* between comps */ int b) {}

            void loopy() {
                for (int i = 0; /* cond */ i < 10; /* update */ i++) {
                    System.out.println(i);
                }
            }

            int[] arr = {1, /* mid */ 2, 3};

            void cast() {
                long n = (/* widen */ long) 5;
            }

            enum E {
                A(/* arg */ 1),
                B(/* arg2 */ 2);

                final int v;

                E(int v) {
                    this.v = v;
                }
            }

            void sw(String s) {
                switch (s) {
                    case /* maybe */ "a" -> {}
                    default -> {}
                }
            }
        }

        // trailing file comment
        """;

    @Test
    void everyCommentPositionIsPreserved() {
        final var before = CommentScanner.scan(SOURCE);
        final var formatted = Grind.format(SOURCE);
        final var afterTexts = CommentScanner.scan(formatted).stream()
            .map(CommentToken::text)
            .toList();
        final var missing = before.stream()
            .map(CommentToken::text)
            .filter(t -> !afterTexts.contains(t))
            .toList();
        assertThat(missing)
            .as("comments lost when formatting:%n%s", formatted)
            .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("commentFixtureInputs")
    void multiLineCommentLayoutIsPreserved(final String name, final String input) {
        final var formatted = Grind.format(input);
        final var before = CommentScanner.scan(input);
        final var after = CommentScanner.scan(formatted);

        assertThat(after)
            .as("comment count must not change for fixture %s%n--- formatted ---%n%s", name, formatted)
            .hasSameSizeAs(before);

        for (var i = 0; i < before.size(); i++) {
            final var src = before.get(i);
            final var dst = after.get(i);
            final var srcLines = src.text().split("\n", -1);
            final var dstLines = dst.text().split("\n", -1);

            assertThat(dstLines.length)
                .as("comment #%d line count must not change (text=%s)", i, src.text())
                .isEqualTo(srcLines.length);

            assertThat(dstLines[0])
                .as("comment #%d first line must not change", i)
                .isEqualTo(srcLines[0]);

            assertThat(continuationShape(dstLines))
                .as("comment #%d continuation line shape must be preserved%n--- formatted ---%n%s", i, formatted)
                .isEqualTo(continuationShape(srcLines));
        }
    }

    private static List<String> continuationShape(final String[] lines) {
        final var minLeading = java.util.Arrays.stream(lines)
            .skip(1)
            .filter(line -> !line.isBlank())
            .mapToInt(CommentAuditTest::leadingCols)
            .min()
            .orElse(0);
        return java.util.Arrays.stream(lines)
            .skip(1)
            .map(line -> {
                if (line.isBlank()) {
                    return "";
                }
                final var stripped = line.substring(consumeCols(line, minLeading));
                return " ".repeat(leadingCols(line) - minLeading) + stripped;
            })
            .toList();
    }

    private static int leadingCols(final String line) {
        var col = 0;
        for (var i = 0; i < line.length(); i++) {
            final var c = line.charAt(i);
            if (c == ' ') {
                col++;
            } else if (c == '\t') {
                col = (col / 4 + 1) * 4;
            } else {
                break;
            }
        }
        return col;
    }

    private static int consumeCols(final String line, final int target) {
        var col = 0;
        var i = 0;
        while (i < line.length() && col < target) {
            final var c = line.charAt(i);
            if (c == ' ') {
                col++;
            } else if (c == '\t') {
                col = (col / 4 + 1) * 4;
            } else {
                break;
            }
            i++;
        }
        return i;
    }

    static Stream<Arguments> commentFixtureInputs() throws URISyntaxException, IOException {
        final var fixturesRoot = CommentAuditTest.class.getClassLoader().getResource("test-fixtures");
        if (fixturesRoot == null) {
            return Stream.empty();
        }
        final var root = Paths.get(fixturesRoot.toURI());
        try (final var listing = Files.list(root)) {
            return listing
                .filter(Files::isDirectory)
                .filter(dir -> dir.getFileName().toString().startsWith("comment-"))
                .map(dir -> Arguments.of(dir.getFileName().toString(), read(dir.resolve("input.java"))))
                .toList()
                .stream();
        }
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
