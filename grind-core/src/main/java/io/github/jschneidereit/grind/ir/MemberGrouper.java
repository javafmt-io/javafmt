package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import javax.lang.model.element.Modifier;

final class MemberGrouper {

    static int group(final Tree member) {
        return switch (member) {
            case VariableTree v -> v.getModifiers().getFlags().contains(Modifier.STATIC) ? 0 : 1;
            case MethodTree m -> {
                if (m.getName().contentEquals("<init>")) {
                    yield 2;
                }
                final var flags = m.getModifiers().getFlags();
                if (flags.contains(Modifier.STATIC)) {
                    yield 7;
                }
                if (flags.contains(Modifier.PUBLIC)) {
                    yield 3;
                }
                if (flags.contains(Modifier.PROTECTED)) {
                    yield 4;
                }
                if (!flags.contains(Modifier.PRIVATE)) {
                    yield 5;
                }
                yield 6;
            }
            case ClassTree c -> 8;
            case null, default -> 9;
        };
    }

    private MemberGrouper() {}
}
