package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Renders one member of a type body. Two member kinds cannot go through
 * {@link Recursor#scan} on their own: a constructor's name is not in its tree (javac calls it
 * {@code <init>}), so it needs the enclosing type's name passed in; and an initializer block is
 * a bare {@link BlockTree} that would otherwise render without its {@code static} keyword.
 * Everything else is scanned normally.
 */
@UtilityClass
final class BodyMemberRenderer {

    @Nullable static Doc render(final Tree member, final String className, final Recursor recursor) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return recursor.attach(
                    mt,
                    recursor.isCompactConstructor(mt)
                            ? CompactConstructorRenderer.render(mt, className, recursor)
                            : ConstructorRenderer.render(mt, className, recursor));
        }
        if (member instanceof BlockTree bt) {
            return recursor.attach(bt, InitBlockRenderer.render(bt, recursor));
        }
        return recursor.scan(member);
    }
}
