package io.javafmt.parser;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class FanOutScannerTest {

    private static final String MIXED = """
            class Mixed {

                boolean value = a + b * c - (d + e) + f(g + h, i - j - k) > 0 && l || m && n;
            }
            """;

    private static final String CALLS = """
            class Calls {

                Object value = a.<T>f(x, y).b.g(z).h().i.j(k.l(m), n(o), () -> q.r(s)).p;
            }
            """;

    private static final String NESTED = """
            class Outer {

                int count;

                int run(final int limit) {
                    for (var i = 0; i < limit; i++) {
                        if (i % 2 == 0) {
                            count += helper(i).get();
                        }
                    }
                    return new Object() {
                        int inner = count + 1;
                    }.inner;
                }

                static class Inner {

                    String name = "x" + "y" + prefix.concat(suffix).trim();
                }
            }
            """;

    @ParameterizedTest
    @ValueSource(strings = {MIXED, CALLS, NESTED, "class Single {\n    int value = a + b;\n}\n", "class None {\n    int value = a;\n}\n"})
    void everyScannerSeesTheEventsItWouldSeeWalkingAlone(final String source) {
        final var tree = JavaParser.parseUnit(source).tree();
        final var alone = List.of(solo(new Events(), tree), solo(new PathEvents(), tree), solo(new Events(), tree));

        final var sharedAll = List.of(new Events(), new PathEvents(), new Events());
        FanOutScanner.walk(tree, sharedAll);

        assertThat(
                sharedAll.stream()
                        .map(Recorder::events)
                        .toList())
                .isEqualTo(alone.stream()
                        .map(Recorder::events)
                        .toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {MIXED, CALLS, NESTED})
    void aScannerThatCountsEverythingVisitsEachNodeOnce(final String source) {
        final var tree = JavaParser.parseUnit(source).tree();
        final var alone = solo(new Events(), tree).events();
        final var shared = new Events();

        FanOutScanner.walk(tree, List.of(shared, new PathEvents()));

        assertThat(shared.events())
                .isNotEmpty()
                .containsExactlyElementsOf(alone);
    }

    private static <S extends ChainScanner<Void, Void> & Recorder> S solo(final S scanner, final com.sun.source.tree.Tree tree) {
        scanner.scan(tree, null);
        return scanner;
    }

    interface Recorder {

        List<String> events();
    }

    private static final class Events extends ChainScanner<Void, Void> implements Recorder {

        private final List<String> events = new ArrayList<>();

        @Override
        public List<String> events() {
            return events;
        }

        @Override
        public Void visitIdentifier(final IdentifierTree node, final Void p) {
            events.add("id " + node.getName());
            return super.visitIdentifier(node, null);
        }

        @Override
        public Void visitLiteral(final LiteralTree node, final Void p) {
            events.add("lit " + node.getValue());
            return super.visitLiteral(node, null);
        }

        @Override
        public Void visitClass(final ClassTree node, final Void p) {
            events.add("class " + node.getSimpleName());
            return super.visitClass(node, null);
        }

        @Override
        protected void visitBinaryOperator(final BinaryTree node, final Void p) {
            events.add("op " + node.getKind());
        }

        @Override
        protected void visitInvocation(final MethodInvocationTree node, final Void p) {
            events.add("call " + node.getMethodSelect());
        }
    }

    private static final class PathEvents extends ChainPathScanner<Void, Void> implements Recorder {

        private final List<String> events = new ArrayList<>();

        @Override
        public List<String> events() {
            return events;
        }

        @Override
        public Void visitIdentifier(final IdentifierTree node, final Void p) {
            events.add(node.getName() + " @ " + kinds());
            return super.visitIdentifier(node, null);
        }

        @Override
        protected void visitBinaryOperator(final BinaryTree node, final Void p) {
            events.add(node.getKind() + " @ " + kinds());
        }

        @Override
        protected void visitInvocation(final MethodInvocationTree node, final Void p) {
            events.add("call @ " + kinds());
        }

        private String kinds() {
            return StreamSupport.stream(getCurrentPath().spliterator(), false)
                    .map(tree -> tree.getKind().toString())
                    .collect(Collectors.joining("<"));
        }
    }
}
