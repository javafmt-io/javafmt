package io.javafmt.parser;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;

import io.javafmt.Position;

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

    @Test
    void parsesSourceWithUtf8Bom() {
        final var bom = "﻿";
        final var tree = JavaParser.parse(bom + "class Foo {}");

        assertThat(tree.getTypeDecls()).hasSize(1);
        final var decl = (ClassTree) tree.getTypeDecls().getFirst();
        assertThat(decl.getSimpleName().toString()).isEqualTo("Foo");
    }

    @Test
    void crlfLineEndingsProduceCorrectLineNumbers() {
        final var unit = JavaParser.parseUnit("class Foo {\r\n    int x;\r\n}\r\n");
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var field = (VariableTree) cls.getMembers().getFirst();

        assertThat(unit.positionOf(field)).isInstanceOf(Position.At.class);
        assertThat(((Position.At) unit.positionOf(field)).line()).isEqualTo(2);
    }

    @Test
    void parseUnitsReturnsOkOutcomesInOrderForValidBatch() {
        final var sources = java.util.List.of(
            "class A {}",
            "class B {}",
            "record C(int x) {}");

        final var outcomes = JavaParser.parseUnits(sources);

        assertThat(outcomes).hasSize(3);
        final var names = outcomes.stream()
            .map(o -> ((ParseOutcome.Ok) o).unit().tree())
            .map(t -> ((ClassTree) t.getTypeDecls().getFirst()).getSimpleName().toString())
            .toList();
        assertThat(names).containsExactly("A", "B", "C");
    }

    @Test
    void parseUnitsSurfacesPerSourceParseFailures() {
        final var sources = java.util.List.of(
            "class A {}",
            "class {",
            "class C {}");

        final var outcomes = JavaParser.parseUnits(sources);

        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.get(0)).isInstanceOf(ParseOutcome.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(ParseOutcome.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(ParseOutcome.Ok.class);
    }

    @Test
    void parseUnitsReturnsEmptyListForEmptyInput() {
        assertThat(JavaParser.parseUnits(java.util.List.of())).isEmpty();
    }

    @Test
    void hasJavacInternalAccessIsFalseWithoutAddExports() {
        // The default build does not add --add-exports for jdk.compiler internals; the
        // probe reflects that. If a future fast path opts into the internal-API path,
        // it should branch on this constant rather than blindly attempting the access.
        assertThat(JavaParser.HAS_JAVAC_INTERNAL_ACCESS).isFalse();
    }

    @Test
    void packageInfoStyleFileHeaderJavadocSurvivesAsFileHeader() {
        // Reproduces a suspected gap in CommentAttacher: a leading Javadoc on a file with
        // only a package declaration should be captured as the file header, not silently lost.
        final var source = """
            /** package summary */
            package com.example;
            """;

        final var unit = JavaParser.parseUnit(source);

        assertThat(unit.fileHeader())
            .extracting(CommentToken::text)
            .containsExactly("/** package summary */");
    }
}
