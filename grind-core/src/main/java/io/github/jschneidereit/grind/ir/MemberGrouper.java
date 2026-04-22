package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import javax.lang.model.element.Modifier;

final class MemberGrouper {

    static int group(final Tree member) {
        if (member instanceof VariableTree v) {
            return v.getModifiers().getFlags().contains(Modifier.STATIC) ? 0 : 1;
        }
        if (member instanceof MethodTree m) {
            final var flags = m.getModifiers().getFlags();
            if (flags.contains(Modifier.STATIC)) return 7;
            if (flags.contains(Modifier.PUBLIC)) return 3;
            if (flags.contains(Modifier.PROTECTED)) return 4;
            if (!flags.contains(Modifier.PRIVATE)) return 5;
            return 6;
        }
        return 8;
    }

    private MemberGrouper() {}
}
