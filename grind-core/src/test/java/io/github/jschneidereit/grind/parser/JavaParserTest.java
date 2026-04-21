package io.github.jschneidereit.grind.parser;

import com.sun.source.tree.ClassTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JavaParserTest {

    @Test
    void parsesSimpleClass() {
        final var tree = JavaParser.parse("class Foo {}");

        assertThat(tree.getTypeDecls()).hasSize(1);
        final var decl = (ClassTree) tree.getTypeDecls().getFirst();
        assertThat(decl.getSimpleName().toString()).isEqualTo("Foo");
    }

    @Test
    void parsesClassWithChainedMethodCall() {
        final var source = """
                class Bar {
                    String greet() {
                        return "hello".toUpperCase().trim();
                    }
                }
                """;

        final var tree = JavaParser.parse(source);

        assertThat(tree.getTypeDecls()).hasSize(1);
        final var decl = (ClassTree) tree.getTypeDecls().getFirst();
        assertThat(decl.getSimpleName().toString()).isEqualTo("Bar");
        assertThat(decl.getMembers()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("topLevelDeclarations")
    void parsesVariousTopLevelDeclarations(final String source, final String expectedName) {
        final var tree = JavaParser.parse(source);

        assertThat(tree.getTypeDecls()).hasSize(1);
        final var decl = (ClassTree) tree.getTypeDecls().getFirst();
        assertThat(decl.getSimpleName().toString()).isEqualTo(expectedName);
    }

    static Stream<Arguments> topLevelDeclarations() {
        return Stream.of(
            arguments("class A {}", "A"),
            arguments("class B { int x; }", "B"),
            arguments("record C(int x, int y) {}", "C")
        );
    }

    @Test
    void throwsOnSyntaxError() {
        assertThatThrownBy(() -> JavaParser.parse("class {"))
                .isInstanceOf(ParseException.class);
    }
}
