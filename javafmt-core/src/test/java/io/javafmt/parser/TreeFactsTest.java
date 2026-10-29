package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import org.junit.jupiter.api.Test;

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
        final var ctor = (MethodTree) record.getMembers()
                .stream()
                .filter(m -> m instanceof MethodTree mt && mt.getName().contentEquals("<init>"))
                .findFirst()
                .orElseThrow();
        final var method = (MethodTree) record.getMembers()
                .stream()
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
        final var vars = enumTree.getMembers()
                .stream()
                .filter(m -> m instanceof VariableTree)
                .map(m -> (VariableTree) m)
                .toList();
        assertThat(facts.isEnumConstant(vars.getFirst())).isTrue();
        assertThat(facts.isEnumConstant(vars.stream().filter(v -> v.getName().contentEquals("LOCK")).findFirst().orElseThrow())).isFalse();
    }
}
