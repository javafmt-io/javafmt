package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import io.javafmt.doc.MemberGroup;

import javax.lang.model.element.Modifier;

final class MemberGrouper {

    static MemberGroup group(final Tree member, final boolean sealedParent) {
        return switch (member) {
            case ClassTree c -> sealedParent ? MemberGroup.NESTED_TYPE_SEALED : MemberGroup.NESTED_TYPE_NORMAL;
            case VariableTree v -> v.getModifiers().getFlags().contains(Modifier.STATIC)
                ? MemberGroup.STATIC_FIELD
                : MemberGroup.INSTANCE_FIELD;
            case BlockTree b -> b.isStatic() ? MemberGroup.STATIC_INITIALIZER : MemberGroup.INSTANCE_INITIALIZER;
            case MethodTree m -> {
                if (m.getName().contentEquals("<init>")) {
                    yield MemberGroup.CONSTRUCTOR;
                }
                final var flags = m.getModifiers().getFlags();
                if (flags.contains(Modifier.STATIC)) {
                    yield MemberGroup.STATIC_METHOD;
                }
                if (flags.contains(Modifier.PUBLIC)) {
                    yield MemberGroup.PUBLIC_METHOD;
                }
                if (flags.contains(Modifier.PROTECTED)) {
                    yield MemberGroup.PROTECTED_METHOD;
                }
                if (!flags.contains(Modifier.PRIVATE)) {
                    yield MemberGroup.PACKAGE_METHOD;
                }
                yield MemberGroup.PRIVATE_METHOD;
            }
            default -> MemberGroup.UNKNOWN;
        };
    }

    private MemberGrouper() {}
}
