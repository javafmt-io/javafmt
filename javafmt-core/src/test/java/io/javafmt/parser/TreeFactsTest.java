package io.javafmt.parser;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TreeFactsTest {

    @Test
    void detectsCompactConstructorAndVarargsAndEnumConstant() {
        final var unit = JavaParser.parseUnit("""
                record R(int x) {
                    R {}
                    static void f(String... rest) {}
                }
                """);
        final var facts = new TreeFacts(unit);
        final var record = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var ctor = (MethodTree) record.getMembers().stream()
                .filter(m -> m instanceof MethodTree mt && mt.getName().contentEquals("<init>"))
                .findFirst()
                .orElseThrow();
        final var method = (MethodTree) record.getMembers().stream()
                .filter(m -> m instanceof MethodTree mt && mt.getName().contentEquals("f"))
                .findFirst()
                .orElseThrow();
        assertThat(facts.isCompactConstructor(ctor)).isTrue();
        assertThat(facts.isVarargs(method.getParameters().getFirst())).isTrue();
    }

    @Test
    void distinguishesEnumConstantFromBodyField() {
        final var unit = JavaParser.parseUnit("""
                enum E {
                    A, B;
                    static final Object LOCK = new Object();
                }
                """);
        final var facts = new TreeFacts(unit);
        final var enumTree = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var vars = enumTree.getMembers().stream()
                .filter(m -> m instanceof VariableTree)
                .map(m -> (VariableTree) m)
                .toList();
        assertThat(facts.isEnumConstant(vars.getFirst())).isTrue();
        assertThat(TreeFacts.isEnumConstant(vars.getFirst(), unit.tree(), unit.sourcePositions()))
                .isTrue();
        assertThat(TreeFacts.isEnumConstant(vars.getLast(), unit.tree(), unit.sourcePositions()))
                .isFalse();
        assertThat(
                facts.isEnumConstant(vars.stream()
                        .filter(v -> v.getName().contentEquals("LOCK"))
                        .findFirst()
                        .orElseThrow()))
                .isFalse();
    }

    static Stream<Arguments> followedByNewlineCases() {
        return Stream.of(
                Arguments.of("class C { int x; int y; }", false),
                Arguments.of("class C { int x; /* a */ int y; }", false),
                Arguments.of("class C { int x; /* a */ /* b */ int y; }", false),
                Arguments.of("class C { int x; /* a\n b */ int y; }", false),
                Arguments.of("class C { int x; /* a */\n int y; }", true),
                Arguments.of("class C { int x; /**/\n int y; }", true),
                Arguments.of("class C { int x; // note\n int y; }", true),
                Arguments.of("class C { int x;\n int y; }", true));
    }

    @ParameterizedTest
    @MethodSource("followedByNewlineCases")
    void followedByNewlineLooksPastCommentsOnTheSameLine(final String source, final boolean expected) {
        final var unit = JavaParser.parseUnit(source);
        final var first = ((ClassTree) unit.tree().getTypeDecls().getFirst()).getMembers()
                .stream()
                .filter(m -> m instanceof VariableTree)
                .findFirst()
                .orElseThrow();
        assertThat(new TreeFacts(unit).isFollowedByNewline(first)).isEqualTo(expected);
    }
}
