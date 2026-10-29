package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.Position;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        return Stream.of(arguments("class A {}", "A"), arguments("class B { int x; }", "B"), arguments("record C(int x, int y) {}", "C"));
    }

    @Test
    void throwsOnSyntaxError() {
        assertThatThrownBy(() -> JavaParser.parse("class {")).isInstanceOf(ParseException.class);
    }

    @Test
    void parsesRecordWithAnnotatedComponents() {
        // Annotations on record components (e.g. JSpecify @Nullable) are legal per JLS;
        // only declaration modifiers like `public` / `final` are disallowed. The parser
        // must allow annotated components through without throwing.
        final var source = """
                import org.jspecify.annotations.Nullable;
                record Slot(@Nullable Object prev, @Nullable Object owner, long lo) {}
                """;
        final var tree = JavaParser.parse(source);
        assertThat(tree.getTypeDecls()).hasSize(1);
        final var decl = (ClassTree) tree.getTypeDecls().getFirst();
        assertThat(decl.getSimpleName().toString()).isEqualTo("Slot");
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
        final var sources = java.util.List.of("class A {}", "class B {}", "record C(int x) {}");
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
        final var sources = java.util.List.of("class A {}", "class {", "class C {}");
        final var outcomes = JavaParser.parseUnits(sources);
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(ParseOutcome.Ok.class);
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

    @Test
    void parseAndAttribute_simpleSource_resolvesFieldType() {
        final var source = """
                class Foo {
                    String greeting = "hi";
                }
                """;
        final var attributed = JavaParser.parseAndAttribute(source, "");
        final var unit = attributed.unit();
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        // Attribution synthesizes a default constructor at the head of the member list,
        // so pick the first VariableTree rather than the first member.
        final var field = (VariableTree) cls.getMembers()
                .stream()
                .filter(VariableTree.class::isInstance)
                .findFirst()
                .orElseThrow();
        final var fieldPath = com.sun.source.util.TreePath.getPath(unit.tree(), field);
        final var fieldType = attributed.attribution().trees().getTypeMirror(fieldPath);
        assertThat(fieldType).isNotNull();
        assertThat(fieldType.toString()).isEqualTo("java.lang.String");
    }

    @Test
    void parseAndAttribute_emptyClasspath_returnsServices() {
        final var attributed = JavaParser.parseAndAttribute("class Foo {}", "");
        assertThat(attributed.attribution().trees()).isNotNull();
        assertThat(attributed.attribution().types()).isNotNull();
        assertThat(attributed.attribution().elements()).isNotNull();
    }

    @Test
    void parseAndAttribute_invalidSource_throwsParseException() {
        assertThatThrownBy(() -> JavaParser.parseAndAttribute("class {", "")).isInstanceOf(ParseException.class);
    }

    @Test
    void parseAndAttribute_batchValidSources_returnsOkForAll() {
        final var sources = java.util.List.of("class A {}", "class B {}", "class C {}");
        final var outcomes = JavaParser.parseAndAttribute(sources, "");
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes).allMatch(o -> o instanceof AttributedOutcome.Ok);
        // All units share the same Attribution since they come from one JavacTask.
        final var attributions = outcomes.stream()
                .map(o -> ((AttributedOutcome.Ok) o).unit().attribution())
                .distinct()
                .toList();
        assertThat(attributions).hasSize(1);
    }

    @Test
    void parseAndAttribute_batchSurfacesPerSourceParseFailures() {
        final var sources = java.util.List.of("class A {}", "class {", "class C {}");
        final var outcomes = JavaParser.parseAndAttribute(sources, "");
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(AttributedOutcome.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(AttributedOutcome.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(AttributedOutcome.Ok.class);
    }

    @Test
    void parseAndAttribute_batchSourceThatCrashesAnalysis_failsOnlyThatSource() {
        // javac's parser folds a long operator chain iteratively but its analyzer walks the
        // resulting tree recursively, so this source parses and then exhausts the stack during
        // analyze(). One such source must not cost the rest of the batch its attribution.
        final var pathological = "class Deep { int value = " + "1 + ".repeat(100_000) + "1; }";
        final var outcomes = JavaParser.parseAndAttribute(java.util.List.of("class A {}", pathological, "class C {}"), "");
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(AttributedOutcome.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(AttributedOutcome.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(AttributedOutcome.Ok.class);
    }

    @Test
    void parseAndAttribute_batchEmpty_returnsEmpty() {
        assertThat(JavaParser.parseAndAttribute(java.util.List.of(), "")).isEmpty();
    }

    @Test
    void parseAndAttribute_batchCrossFileTypesResolve() {
        // Class B references type A — only resolvable when both are attributed together.
        final var sources = java.util.List.of("class A { int value() { return 42; } }", "class B { A field; }");
        final var outcomes = JavaParser.parseAndAttribute(sources, "");
        final var unitB = ((AttributedOutcome.Ok) outcomes.get(1)).unit();
        final var clsB = (ClassTree) unitB.unit().tree().getTypeDecls().getFirst();
        final var field = (VariableTree) clsB.getMembers()
                .stream()
                .filter(VariableTree.class::isInstance)
                .findFirst()
                .orElseThrow();
        final var fieldType = unitB.attribution()
                .trees()
                .getTypeMirror(com.sun.source.util.TreePath.getPath(unitB.unit().tree(), field));
        assertThat(fieldType).isNotNull();
        assertThat(fieldType.toString()).isEqualTo("A");
    }

    @Test
    void attributionFileManagersDoNotLeakClasspathAcrossCalls() {
        // The attributed path reuses one file manager per classpath. Reuse must not let one
        // classpath's locations bleed into another's, in either direction.
        final var source = """
            class Fixture {

                io.javafmt.lint.LintEdit edit() {
                    return null;
                }
            }
            """;
        final var realClasspath = System.getProperty("java.class.path");
        // Not "": an empty classpath option is omitted entirely, which leaves javac on the ambient
        // java.class.path — where the type would resolve after all.
        final var emptyClasspath = System.getProperty("java.io.tmpdir") + "/javafmt-no-such-classpath";
        assertThat(resolvesLintEdit(source, realClasspath)).isTrue();
        assertThat(resolvesLintEdit(source, emptyClasspath)).isFalse();
        assertThat(resolvesLintEdit(source, realClasspath)).isTrue();
    }

    private static boolean resolvesLintEdit(final String source, final String classpath) {
        final var attributed = JavaParser.parseAndAttribute(source, classpath);
        final var trees = attributed.attribution().trees();
        final var found = new boolean[1];
        new com.sun.source.util.TreePathScanner<Void, Void>() {

            @Override
            public Void visitMethod(final com.sun.source.tree.MethodTree node, final Void p) {
                // Constructors have no return type, and javac synthesizes one for every class.
                final var returnType = node.getReturnType();
                if (returnType != null) {
                    final var type = trees.getTypeMirror(new com.sun.source.util.TreePath(getCurrentPath(), returnType));
                    if (type != null && type.getKind() == javax.lang.model.type.TypeKind.DECLARED) {
                        found[0] = true;
                    }
                }
                return super.visitMethod(node, null);
            }
        }.scan(new com.sun.source.util.TreePath(attributed.unit().tree()), null);
        return found[0];
    }
}
