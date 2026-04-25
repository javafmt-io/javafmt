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

    @Test
    void trailingLineCommentStaysTrailing_doesNotBecomeLeadingOnNext() {
        final var source = """
            class C {
                int x; // suffix
                int y;
            }
            """;

        final var formatted = Grind.format(source);
        final var lines = formatted.split("\n", -1);

        final var xLine = lineContaining(lines, "int x;");
        final var yLine = lineContaining(lines, "int y;");

        assertThat(xLine).as("trailing comment must stay on the same line as `int x;` in:%n%s", formatted)
            .contains("// suffix");
        assertThat(yLine).as("trailing comment must not migrate to leading of `int y;` in:%n%s", formatted)
            .doesNotContain("// suffix");
    }

    @Test
    void leadingJavadocStaysLeading_doesNotBecomeTrailingOnPrev() {
        final var source = """
            class C {
                int x;

                /** doc on y */
                int y;
            }
            """;

        final var formatted = Grind.format(source);
        final var lines = formatted.split("\n", -1);

        final var docLineIdx = indexOfLineContaining(lines, "/** doc on y */");
        final var yLineIdx = indexOfLineContaining(lines, "int y;");

        assertThat(docLineIdx).as("javadoc must appear in:%n%s", formatted).isNotNegative();
        assertThat(yLineIdx).as("`int y;` must appear in:%n%s", formatted).isNotNegative();
        assertThat(docLineIdx).as("javadoc must remain on a line before `int y;` in:%n%s", formatted)
            .isLessThan(yLineIdx);
        assertThat(lines[docLineIdx]).as("javadoc must not piggyback onto `int x;` in:%n%s", formatted)
            .doesNotContain("int x;");
    }

    @Test
    void interiorBlockCommentStaysInsideBraces() {
        final var source = """
            class C {
                void m() {
                    // pure interior
                }
            }
            """;

        final var formatted = Grind.format(source);
        final var lines = formatted.split("\n", -1);

        final var openIdx = indexOfLineContaining(lines, "void m() {");
        final var commentIdx = indexOfLineContaining(lines, "// pure interior");

        assertThat(openIdx).as("opening brace of `m` must appear in:%n%s", formatted).isNotNegative();
        assertThat(commentIdx).as("interior comment must appear in:%n%s", formatted).isNotNegative();
        assertThat(commentIdx).as("interior comment must stay inside the method body in:%n%s", formatted)
            .isGreaterThan(openIdx);

        final var closeIdx = indexOfFirstLineEqualingFrom(lines, commentIdx, "    }");
        assertThat(closeIdx).as("closing brace of `m` must follow the interior comment in:%n%s", formatted)
            .isNotNegative();
    }

    private static String lineContaining(final String[] lines, final String needle) {
        return java.util.Arrays.stream(lines)
            .filter(l -> l.contains(needle))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no line contains: " + needle));
    }

    private static int indexOfLineContaining(final String[] lines, final String needle) {
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfFirstLineEqualingFrom(final String[] lines, final int from, final String exact) {
        for (var i = from; i < lines.length; i++) {
            if (lines[i].equals(exact)) {
                return i;
            }
        }
        return -1;
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
