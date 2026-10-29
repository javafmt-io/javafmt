package io.javafmt.builder;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import io.javafmt.doc.Doc;
import io.javafmt.doc.LeadingCommentAttacher;
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

    final ConstructorRendering PLAIN_CONSTRUCTOR = ConstructorRenderer::render;

    @Nullable static Doc render(
            final Tree member,
            final String className,
            final Recursor recursor,
            final LeadingCommentAttacher attacher,
            final ConstructorRendering constructorRendering) {
        if (member instanceof MethodTree mt && mt.getName().contentEquals("<init>")) {
            return attacher.attach(mt, constructorRendering.render(mt, className, recursor, attacher));
        }
        if (member instanceof BlockTree bt) {
            return attacher.attach(bt, InitBlockRenderer.render(bt, recursor, attacher));
        }
        return recursor.scan(member);
    }

    /**
     * How a type renders its constructors. Classes and enums always use
     * {@link ConstructorRenderer}; only a record has a second, compact form to choose between,
     * so only a record pays the cost of asking.
     */
    @FunctionalInterface
    interface ConstructorRendering {

        Doc render(MethodTree ctor, String className, Recursor recursor, LeadingCommentAttacher attacher);
    }
}
