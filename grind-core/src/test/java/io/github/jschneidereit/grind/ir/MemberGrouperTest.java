package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.VariableTree;

import io.github.jschneidereit.grind.parser.JavaParser;

import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MemberGrouperTest {

    private static final String SOURCE = """
        class Host {
            static int STATIC = 1;
            int instance = 2;
            static { int x = 0; }
            { int y = 0; }
            Host() {}
            public void pub() {}
            protected void prot() {}
            void pkg() {}
            private void priv() {}
            private static void help() {}
            class Inner {}
        }
        """;

    private static Tree unknownTree() {
        return new Tree() {
            @Override
            public Kind getKind() {
                return Kind.OTHER;
            }

            @Override
            public <R, D> R accept(final TreeVisitor<R, D> visitor, final D data) {
                return visitor.visitOther(this, data);
            }
        };
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("mappings")
    void group(final Tree member, final boolean sealedParent, final String label, final MemberGroup expected) {
        assertThat(MemberGrouper.group(member, sealedParent)).isEqualTo(expected);
    }

    static Stream<Arguments> mappings() {
        final var unit = JavaParser.parseUnit(SOURCE);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var members = cls.getMembers();

        final var staticField = members.stream()
            .filter(m -> m instanceof VariableTree v && v.getModifiers().getFlags().contains(Modifier.STATIC))
            .findFirst().orElseThrow();
        final var instanceField = members.stream()
            .filter(m -> m instanceof VariableTree v && !v.getModifiers().getFlags().contains(Modifier.STATIC))
            .findFirst().orElseThrow();
        final var staticBlock = members.stream()
            .filter(m -> m instanceof BlockTree b && b.isStatic())
            .findFirst().orElseThrow();
        final var instanceBlock = members.stream()
            .filter(m -> m instanceof BlockTree b && !b.isStatic())
            .findFirst().orElseThrow();
        final var constructor = members.stream()
            .filter(m -> m instanceof MethodTree mt && mt.getName().contentEquals("<init>"))
            .findFirst().orElseThrow();
        final var publicMethod = members.stream()
            .filter(m -> m instanceof MethodTree mt && mt.getModifiers().getFlags().contains(Modifier.PUBLIC))
            .findFirst().orElseThrow();
        final var protectedMethod = members.stream()
            .filter(m -> m instanceof MethodTree mt && mt.getModifiers().getFlags().contains(Modifier.PROTECTED))
            .findFirst().orElseThrow();
        final var packageMethod = members.stream()
            .filter(m -> m instanceof MethodTree mt
                && !mt.getName().contentEquals("<init>")
                && !mt.getModifiers().getFlags().contains(Modifier.PUBLIC)
                && !mt.getModifiers().getFlags().contains(Modifier.PROTECTED)
                && !mt.getModifiers().getFlags().contains(Modifier.PRIVATE)
                && !mt.getModifiers().getFlags().contains(Modifier.STATIC))
            .findFirst().orElseThrow();
        final var privateMethod = members.stream()
            .filter(m -> m instanceof MethodTree mt
                && mt.getModifiers().getFlags().contains(Modifier.PRIVATE)
                && !mt.getModifiers().getFlags().contains(Modifier.STATIC))
            .findFirst().orElseThrow();
        final var staticMethod = members.stream()
            .filter(m -> m instanceof MethodTree mt && mt.getModifiers().getFlags().contains(Modifier.STATIC))
            .findFirst().orElseThrow();
        final var innerClass = members.stream()
            .filter(m -> m instanceof ClassTree)
            .findFirst().orElseThrow();

        return Stream.of(
            Arguments.of(staticField,    false, "STATIC_FIELD",       MemberGroup.STATIC_FIELD),
            Arguments.of(instanceField,  false, "INSTANCE_FIELD",     MemberGroup.INSTANCE_FIELD),
            Arguments.of(staticBlock,    false, "STATIC_INITIALIZER", MemberGroup.STATIC_INITIALIZER),
            Arguments.of(instanceBlock,  false, "INSTANCE_INITIALIZER", MemberGroup.INSTANCE_INITIALIZER),
            Arguments.of(constructor,    false, "CONSTRUCTOR",         MemberGroup.CONSTRUCTOR),
            Arguments.of(publicMethod,   false, "PUBLIC_METHOD",       MemberGroup.PUBLIC_METHOD),
            Arguments.of(protectedMethod, false, "PROTECTED_METHOD",   MemberGroup.PROTECTED_METHOD),
            Arguments.of(packageMethod,  false, "PACKAGE_METHOD",      MemberGroup.PACKAGE_METHOD),
            Arguments.of(privateMethod,  false, "PRIVATE_METHOD",      MemberGroup.PRIVATE_METHOD),
            Arguments.of(staticMethod,   false, "STATIC_METHOD",       MemberGroup.STATIC_METHOD),
            Arguments.of(innerClass,     false, "NESTED_TYPE_NORMAL",  MemberGroup.NESTED_TYPE_NORMAL),
            Arguments.of(innerClass,     true,  "NESTED_TYPE_SEALED",  MemberGroup.NESTED_TYPE_SEALED),
            Arguments.of(unknownTree(),  false, "UNKNOWN",             MemberGroup.UNKNOWN)
        );
    }
}
