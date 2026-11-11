package io.javafmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MisplacedCommentTest {

    private static final String MESSAGE = "a comment goes on its own line or at the end of a line, not between code";

    @ParameterizedTest
    @MethodSource("commentsBetweenCode")
    void aCommentWithCodeOnBothSidesOfItIsAnErrorAndTheFileIsLeftAsWritten(final String source,
                                                                           final String comment,
                                                                           final int line,
                                                                           final int column) {
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        assertThat(result.output()).isEqualTo(source);
        assertThat(result.diagnostics()).containsExactly(new Diagnostic.LintError(MESSAGE, new Position.At(line, column, source.indexOf(comment))));
    }

    static Stream<Arguments> commentsBetweenCode() {
        return Stream.of(
                Arguments.of("class A {\n    int f() {\n        return g(/* count */ 3);\n    }\n}\n", "/* count */", 3, 18),
                Arguments.of("class A {\n    public /* why */ int x;\n}\n", "/* why */", 2, 12),
                Arguments.of("class A {\n    int x = /* a\n       b */ 1;\n}\n", "/* a", 2, 13),
                Arguments.of(
                        "class A {\n    void f(boolean b) {\n        if (b) {\n            g();\n        } else /* otherwise */ {\n            h();\n        }\n    }\n}\n",
                        "/* otherwise */",
                        5,
                        16));
    }

    @Test
    void everyCommentBetweenCodeIsReported() {
        final var source = "class A {\n    int x = /* one */ 1 + /* two */ 2;\n}\n";
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        assertThat(result.output()).isEqualTo(source);
        assertThat(result.diagnostics())
                .containsExactly(new Diagnostic.LintError(MESSAGE, new Position.At(2, 13, source.indexOf("/* one */"))),
                        new Diagnostic.LintError(MESSAGE, new Position.At(2, 27, source.indexOf("/* two */"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class A {\n\n    int x; // at the end of a line\n}\n",
            "class A {\n\n    int x; /* at the end of a line */\n}\n",
            "class A {\n\n    int x; /* followed by */ // another comment\n}\n",
            "class A {\n\n    // on its own line\n    int x;\n}\n",
    })
    void aCommentOnItsOwnLineOrAtTheEndOfALineIsAllowed(final String source) {
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.output()).isEqualTo(source);
    }

    @Test
    void aBlockCommentThatStartsALineBeforeCodeMovesOntoALineOfItsOwn() {
        final var source = "class A {\n\n    /* leading */ int x;\n}\n";
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.output()).isEqualTo("class A {\n\n    /* leading */\n    int x;\n}\n");
    }
}
