package io.github.jschneidereit.grind.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;

import org.junit.jupiter.api.Test;

class CommentAttacherTest {

    @Test
    void commentInsideEmptyMethodBlockAttachesAsInteriorOfTheInnerBlock() {
        // Documents the slot-overlap policy: when several slots cover the same offset,
        // the sweep matches the slot with the smallest `hi`, i.e. the innermost.
        // An interior comment sits inside the method body block AND inside the broader
        // class-body region; it must land on the block, not bubble out to the class.
        final var source = """
            class C {
                void m() {
                    // pure interior
                }
            }
            """;

        final var unit = JavaParser.parseUnit(source);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var method = (MethodTree) cls.getMembers().getFirst();
        final var body = method.getBody();

        assertThat(unit.interiorOf(body))
            .extracting(CommentToken::text)
            .containsExactly("// pure interior");
        assertThat(unit.leadingOf(method)).isEmpty();
        assertThat(unit.leadingOf(cls)).isEmpty();
    }
}
