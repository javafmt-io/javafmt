package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import javax.lang.model.element.Modifier;

final class MemberGrouper {

    static int group(final Tree member, final boolean sealedParent) {
        return switch (member) {
            case ClassTree c -> sealedParent ? 0 : 11;
            case VariableTree v -> v.getModifiers().getFlags().contains(Modifier.STATIC) ? 1 : 3;
            case BlockTree b -> b.isStatic() ? 2 : 4;
            case MethodTree m -> {
                if (m.getName().contentEquals("<init>")) {
                    yield 5;
                }
                final var flags = m.getModifiers().getFlags();
                if (flags.contains(Modifier.STATIC)) {
                    yield 10;
                }
                if (flags.contains(Modifier.PUBLIC)) {
                    yield 6;
                }
                if (flags.contains(Modifier.PROTECTED)) {
                    yield 7;
                }
                if (!flags.contains(Modifier.PRIVATE)) {
                    yield 8;
                }
                yield 9;
            }
            case null, default -> 12;
        };
    }

    private MemberGrouper() {}
}
