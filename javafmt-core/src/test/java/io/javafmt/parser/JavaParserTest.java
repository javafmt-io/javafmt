package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import io.javafmt.Position;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.StandardJavaFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavaParserTest {

    @Test
    void parsingOnManyShortLivedThreadsKeepsNoFileManagerPerThread() {
        final var before = JavaParser.idleFileManagers();
        IntStream.range(0, 20)
                .forEach(i -> {
                    final var thread = Thread.ofPlatform().start(() -> JavaParser.parse("class T" + i + " {}"));
                    try {
                        thread.join();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
        assertThat(JavaParser.idleFileManagers()).isLessThanOrEqualTo(Math.max(before, 1));
    }

    @Test
    void reattributingReusesTheSessionOfTheOriginalAttribution() {
        final var sessions = JavaParser.attributing(
                "",
                session -> {
                    final var original = session.attribute("class A { java.util.List<String> xs = java.util.List.of(); }");
                    final var reattributed = JavaParser.reattribute(
                            "class A { java.util.List<String> ys = java.util.List.of(); }",
                            original.attribution());
                    return List.of(session, original.attribution().session(), reattributed.attribution().session());
                });
        assertThat(sessions).allSatisfy(s -> assertThat(s).isSameAs(sessions.getFirst()));
    }

    @Test
    void concurrentSessionsOnOneClasspathNeverShareAFileManager() throws InterruptedException {
        final var held = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var first = new AtomicReference<StandardJavaFileManager>();
        final var holder = Thread.ofPlatform()
                .start(
                        () -> JavaParser.attributing(
                                "",
                                session -> {
                                    first.set(session.fileManager());
                                    held.countDown();
                                    awaitUninterruptibly(release);
                                    return session;
                                }));
        held.await();
        final var second = JavaParser.attributing("", session -> session.fileManager());
        release.countDown();
        holder.join();
        assertThat(second).isNotSameAs(first.get());
    }

    @Test
    void aSessionRefusesToAttributeAfterItsAttributingCallReturns() {
        final var session = JavaParser.attributing("", Function.identity());
        assertThatThrownBy(() -> session.attribute("class A {}")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void attributingTwiceOnOneClasspathReusesTheFileManager() {
        final var first = JavaParser.attributing("", session -> session.fileManager());
        final var second = JavaParser.attributing("", session -> session.fileManager());
        assertThat(second).isSameAs(first);
    }

    @Test
    void attributingOnDifferentClasspathsNeverSharesAFileManager() {
        final var plain = JavaParser.attributing("", session -> session.fileManager());
        final var other = JavaParser.attributing(System.getProperty("java.class.path"), session -> session.fileManager());
        assertThat(other).isNotSameAs(plain);
    }

    @Test
    void idleAttributionFileManagersAreBoundedAcrossManyClasspaths() {
        final var capacity = JavaParser.ATTRIBUTION_POOL_CAPACITY;
        final var prefix = System.getProperty("java.io.tmpdir") + "/javafmt-classpath-";
        IntStream.range(0, capacity + 5).forEach(i -> JavaParser.attributing(prefix + i, session -> session.attribute("class A {}")));
        assertThat(JavaParser.idleAttributionFileManagers()).isLessThanOrEqualTo(capacity);
    }

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
                .map(o -> o.orElseThrow().tree())
                .map(t -> ((ClassTree) t.getTypeDecls().getFirst()).getSimpleName().toString())
                .toList();
        assertThat(names).containsExactly("A", "B", "C");
    }

    @Test
    void parseUnitsSurfacesPerSourceParseFailures() {
        final var sources = java.util.List.of("class A {}", "class {", "class C {}");
        final var outcomes = JavaParser.parseUnits(sources);
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(Staged.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(Staged.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(Staged.Ok.class);
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
    void attribute_simpleSource_resolvesFieldType() {
        final var source = """
                class Foo {
                    String greeting = "hi";
                }
                """;
        final var fieldType = JavaParser.attributing("", session -> firstFieldType(session.attribute(source)));
        assertThat(fieldType).isEqualTo("java.lang.String");
    }

    @Test
    void attribute_emptyClasspath_returnsServices() {
        JavaParser.attributing(
                "",
                session -> {
                    final var attribution = session.attribute("class Foo {}").attribution();
                    assertThat(attribution.trees()).isNotNull();
                    assertThat(attribution.types()).isNotNull();
                    assertThat(attribution.elements()).isNotNull();
                    return attribution;
                });
    }

    @Test
    void attribute_invalidSource_throwsParseException() {
        assertThatThrownBy(() -> JavaParser.attributing("", session -> session.attribute("class {"))).isInstanceOf(ParseException.class);
    }

    @Test
    void attribute_batchValidSources_returnsOkForAll() {
        final var sources = java.util.List.of("class A {}", "class B {}", "class C {}");
        final var outcomes = JavaParser.attributing("", session -> session.attribute(sources));
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes).allMatch(o -> o instanceof Staged.Ok);
        // All units share the same Attribution since they come from one JavacTask.
        final var attributions = outcomes.stream()
                .map(o -> o.orElseThrow().attribution())
                .distinct()
                .toList();
        assertThat(attributions).hasSize(1);
    }

    @Test
    void attribute_batchSurfacesPerSourceParseFailures() {
        final var sources = java.util.List.of("class A {}", "class {", "class C {}");
        final var outcomes = JavaParser.attributing("", session -> session.attribute(sources));
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(Staged.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(Staged.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(Staged.Ok.class);
    }

    @Test
    void attribute_batchSourceThatCrashesAnalysis_failsOnlyThatSource() {
        // javac's parser folds a long operator chain iteratively but its analyzer walks the
        // resulting tree recursively, so this source parses and then exhausts the stack during
        // analyze(). One such source must not cost the rest of the batch its attribution.
        final var pathological = "class Deep { int value = " + "1 + ".repeat(100_000) + "1; }";
        final var outcomes = JavaParser.attributing("", session -> session.attribute(java.util.List.of("class A {}", pathological, "class C {}")));
        assertThat(outcomes).hasSize(3);
        assertThat(outcomes.getFirst()).isInstanceOf(Staged.Ok.class);
        assertThat(outcomes.get(1)).isInstanceOf(Staged.Failed.class);
        assertThat(outcomes.get(2)).isInstanceOf(Staged.Ok.class);
    }

    @Test
    void attribute_batchEmpty_returnsEmpty() {
        final var outcomes = JavaParser.attributing("", session -> session.attribute(List.<String>of()));
        assertThat(outcomes).isEmpty();
    }

    @Test
    void attribute_batchCrossFileTypesResolve() {
        // Class B references type A — only resolvable when both are attributed together.
        final var sources = java.util.List.of("class A { int value() { return 42; } }", "class B { A field; }");
        final var fieldType = JavaParser.attributing("", session -> firstFieldType(session.attribute(sources).get(1).orElseThrow()));
        assertThat(fieldType).isEqualTo("A");
    }

    @Test
    void attributionFileManagersDoNotLeakClasspathAcrossCalls() {
        // The attributed path pools file managers per classpath. Reuse must not let one
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

    private static String firstFieldType(final AttributedUnit attributed) {
        final var tree = attributed.unit().tree();
        final var cls = (ClassTree) tree.getTypeDecls().getFirst();
        // Attribution synthesizes a default constructor at the head of the member list,
        // so pick the first VariableTree rather than the first member.
        final var field = cls.getMembers()
                .stream()
                .filter(VariableTree.class::isInstance)
                .findFirst()
                .orElseThrow();
        return String.valueOf(attributed.attribution().trees().getTypeMirror(com.sun.source.util.TreePath.getPath(tree, field)));
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static boolean resolvesLintEdit(final String source, final String classpath) {
        return JavaParser.attributing(classpath, session -> resolvesLintEdit(session.attribute(source)));
    }

    private static boolean resolvesLintEdit(final AttributedUnit attributed) {
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
