package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreeScanner;
import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReassignmentDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {"x = 1;", "x += 1;", "x -= 1;", "x++;", "++x;", "x--;", "--x;"})
    void detectsWritesToTheNamedVariable(final String statement) {
        assertThat(detects("x", statement)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"y = 1;", "y += x;", "y++;", "int z = x + 1;", "System.out.println(x);"})
    void ignoresStatementsThatDoNotWriteTheNamedVariable(final String statement) {
        assertThat(detects("x", statement)).isFalse();
    }

    @Test
    void ignoresWritesInsideLambdaBodies() {
        // A lambda is a separate scope. Java already requires a captured local to be effectively
        // final, so an assignment to `x` inside a lambda must target a different variable.
        assertThat(detects("x", """
            Runnable r = () -> {
                int x = 0;
                x = 1;
            };
            """))
                .isFalse();
    }

    @Test
    void ignoresWritesInsideClassDeclarations() {
        assertThat(detects("x", """
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    int x = 0;
                    x = 1;
                }
            };
            """))
                .isFalse();
    }

    @Test
    void arrayElementAssignmentIsNotAReassignment() {
        // `a[0] = 1` mutates the array, not the reference, so `final int[] a` still compiles.
        // Detecting it would be a behaviour change, not a refactor.
        assertThat(detects("a", "a[0] = 1;")).isFalse();
    }

    @Test
    void isNotFoundBeforeScanning() {
        assertThat(new ReassignmentDetector("x").found()).isFalse();
    }

    private static boolean detects(final String name, final String statements) {
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test(int x, int y, int[] a) {
            %s
                }
            }
            """.formatted(statements.stripTrailing().indent(8)));
        final var detector = new ReassignmentDetector(name);
        detector.scan(bodyOfTest(unit.tree()), null);
        return detector.found();
    }

    private static BlockTree bodyOfTest(final com.sun.source.tree.CompilationUnitTree tree) {
        final var found = new TreeScanner<Void, Void>() {

            private BlockTree body;

            @Override
            public Void visitMethod(final MethodTree node, final Void p) {
                if (node.getName().contentEquals("test")) {
                    body = node.getBody();
                }
                return super.visitMethod(node, null);
            }

            BlockTree body() {
                return body;
            }
        };
        found.scan(tree, null);
        return found.body();
    }
}
