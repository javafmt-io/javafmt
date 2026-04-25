package io.github.jschneidereit.grind.ir;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import io.github.jschneidereit.grind.GrindConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;

final class MemberReorderer {

    static Stream<? extends Tree> reorder(
            final Stream<? extends Tree> members,
            final GrindConfig config,
            final boolean sealedParent,
            final Recursor recursor) {
        if (!config.reorderMembers()) {
            return members;
        }
        final var list = members.toList();
        if (hasForwardReferenceProblem(list, recursor)) {
            return list.stream();
        }
        return list.stream().sorted(Comparator.comparing(m -> MemberGrouper.group(m, sealedParent)));
    }

    private static boolean hasForwardReferenceProblem(final List<? extends Tree> members, final Recursor recursor) {
        final var fieldIndex = new HashMap<String, Integer>();
        for (var i = 0; i < members.size(); i++) {
            if (members.get(i) instanceof VariableTree v) {
                fieldIndex.putIfAbsent(v.getName().toString(), i);
            }
        }
        for (var i = 0; i < members.size(); i++) {
            if (!(members.get(i) instanceof VariableTree v)) {
                continue;
            }
            final var flags = v.getModifiers().getFlags();
            if (!flags.contains(Modifier.STATIC) || !flags.contains(Modifier.FINAL)) {
                continue;
            }
            final var init = v.getInitializer();
            if (init == null) {
                continue;
            }
            for (final var ref : collectIdentifiers(init)) {
                final var idx = fieldIndex.get(ref);
                if (idx != null && idx > i) {
                    recursor.emitWarning(
                        "skipping member reorder: forward reference to '" + ref
                            + "' in static-final initializer of '" + v.getName() + "'",
                        v);
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> collectIdentifiers(final Tree subtree) {
        final var names = new ArrayList<String>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void p) {
                names.add(node.getName().toString());
                return null;
            }
        }.scan(subtree, null);
        return names;
    }

    private MemberReorderer() {}
}
