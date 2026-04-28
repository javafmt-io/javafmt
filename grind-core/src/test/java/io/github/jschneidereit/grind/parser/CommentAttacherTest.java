package io.github.jschneidereit.grind.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import org.junit.jupiter.api.Test;

class CommentAttacherTest {

    @Test
    void commentBetweenConsecutiveClassMembers_attachesAsLeadingOfSecond() {
        final var source = """
            class C {
                int x;
                /* between */
                int y;
            }
            """;

        final var unit = JavaParser.parseUnit(source);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var vars = cls.getMembers().stream()
            .filter(m -> m instanceof VariableTree)
            .toList();
        final var x = vars.get(0);
        final var y = vars.get(1);

        assertThat(unit.leadingOf(y))
            .extracting(CommentToken::text)
            .containsExactly("/* between */");
        assertThat(unit.trailingOf(x)).isEmpty();
    }

    @Test
    void trailingLineCommentAfterStatement_attachesAsTrailingOfThatStatement() {
        final var source = """
            class C {
                void m() {
                    int x = 1; // after-x
                    int y = 2;
                }
            }
            """;

        final var unit = JavaParser.parseUnit(source);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();
        final var method = (MethodTree) cls.getMembers().stream()
            .filter(m -> m instanceof MethodTree mt && !mt.getName().contentEquals("<init>"))
            .findFirst().orElseThrow();
        final var body = method.getBody();
        final var x = body.getStatements().stream()
            .filter(s -> s instanceof VariableTree vt && vt.getName().contentEquals("x"))
            .findFirst().orElseThrow();

        assertThat(unit.trailingOf(x))
            .extracting(CommentToken::text)
            .containsExactly("// after-x");
        assertThat(unit.leadingOf(body.getStatements().get(1))).isEmpty();
    }

    @Test
    void javadocBeforeFirstTypeWithNoImports_attachesAsFileHeader() {
        // When there is no package or import before the class, the region from
        // offset 0 to the class start is a FILE_HEADER slot — the comment has
        // no LEADING slot to land in and goes to fileHeader, not leadingOf(class).
        final var source = """
            /** file header */
            class C {}
            """;

        final var unit = JavaParser.parseUnit(source);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();

        assertThat(unit.fileHeader())
            .extracting(CommentToken::text)
            .containsExactly("/** file header */");
        assertThat(unit.leadingOf(cls)).isEmpty();
    }

    @Test
    void commentBetweenImportAndFirstType_attachesAsLeadingOfType() {
        // With an import preceding the class, there is a LEADING slot from the end
        // of the import to the start of the class. The comment is not on the same
        // line as the import, so it is not trailing-of-import; instead it is
        // leading-of-class.
        final var source = """
            import java.util.List;
            /** class-doc */
            class C {}
            """;

        final var unit = JavaParser.parseUnit(source);
        final var cls = (ClassTree) unit.tree().getTypeDecls().getFirst();

        assertThat(unit.leadingOf(cls))
            .extracting(CommentToken::text)
            .containsExactly("/** class-doc */");
        assertThat(unit.fileHeader()).isEmpty();
    }

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
