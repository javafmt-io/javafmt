package io.javafmt.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompilerOutputTest {
    @Test
    void anOutOfMemoryErrorJavacSwallowedIsThrownAgain() {
        final var output = """
                An exception has occurred in the compiler (25.0.5). Please file a bug against the Java compiler.
                java.lang.OutOfMemoryError: Java heap space
                	at jdk.compiler/com.sun.tools.javac.util.Position$LineMapImpl.build(Position.java:174)
                """;
        assertThatThrownBy(() -> JavaParser.rethrowOutOfMemory(output))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Java heap space");
    }

    @Test
    void otherCompilerOutputIsNotAnError() {
        assertThatCode(() -> JavaParser.rethrowOutOfMemory("An exception has occurred in the compiler.\njava.lang.NullPointerException\n"))
                .doesNotThrowAnyException();
    }
}
