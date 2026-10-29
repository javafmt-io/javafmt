package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChainScannerTest {

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

    @ParameterizedTest
    @ValueSource(strings = {MIXED, CALLS, "class Single {\n\n    int value = a + b;\n}\n", "class None {\n\n    int value = a;\n}\n"})
    void visitsOperatorsAndOperandsInTheSameOrderAsTreeScanner(final String source) {
        final var unit = JavaParser.parseUnit(source).tree();
        assertThat(chainVisits(unit)).isEqualTo(javacVisits(unit));
    }

    @ParameterizedTest
    @ValueSource(strings = {MIXED, CALLS, "class Single {\n\n    int value = a + b;\n}\n"})
    void reportsTheSamePathAsTreePathScanner(final String source) {
        final var unit = JavaParser.parseUnit(source).tree();
        assertThat(chainPaths(unit)).isEqualTo(javacPaths(unit));
    }

    static Stream<Arguments> reductions() {
        return Stream.of("int value = 1 + 2 * 3 - 4 + 5;", "Object value = f(1).<T>g(2, 3).h.i(4).j(k(5).l(6));")
                .flatMap(field -> Stream.of(Arguments.of(field, false), Arguments.of(field, true)));
    }

    @ParameterizedTest
    @MethodSource("reductions")
    void reducesInTheSameOrderAsTreeScanner(final String field, final boolean concatenating) {
        final var unit = JavaParser.parseUnit("class Sum {\n\n    " + field + "\n}\n").tree();
        final var javac = new TreeScanner<String, Void>() {

            @Override
            public String visitLiteral(final LiteralTree node, final Void unused) {
                return String.valueOf(node.getValue());
            }

            @Override
            public String reduce(final String r1, final String r2) {
                return concatenating ? r1 + "<" + r2 : super.reduce(r1, r2);
            }
        };
        final var chain = new ChainScanner<String, Void>() {

            @Override
            public String visitLiteral(final LiteralTree node, final Void unused) {
                return String.valueOf(node.getValue());
            }

            @Override
            public String reduce(final String r1, final String r2) {
                return concatenating ? r1 + "<" + r2 : super.reduce(r1, r2);
            }
        };
        assertThat(chain.scan(unit, null)).isEqualTo(javac.scan(unit, null));
    }

    static Stream<Arguments> deepChains() {
        return Stream.of(
                Arguments.of("int value = " + Stream.generate(() -> "1").limit(5_000).collect(Collectors.joining(" + ")) + ";", BinaryTree.class),
                Arguments.of("Object value = new StringBuilder()" + ".append(1)".repeat(5_000) + ";", MethodInvocationTree.class),
                Arguments.of("Object value = a" + ".f(1).b".repeat(5_000) + ";", MethodInvocationTree.class));
    }

    @ParameterizedTest
    @MethodSource("deepChains")
    void walksAChainOnAStackTooSmallForTreeScannerToRecurseThrough(final String field, final Class<?> literalParent) throws InterruptedException {
        final var terms = 5_000;
        final var source = "class Deep {\n\n    " + field + "\n}\n";
        final var unit = JavaParser.parseUnit(source).tree();
        final var literals = new AtomicInteger();
        assertThat(
                onASmallStack(
                        () -> new TreeScanner<Void, Void>() {
                        }.scan(unit, null)))
                .isInstanceOf(StackOverflowError.class);
        assertThat(
                onASmallStack(
                        () -> new ChainPathScanner<Void, Void>() {

                            @Override
                            public Void visitLiteral(final LiteralTree node, final Void unused) {
                                assertThat(getCurrentPath().getParentPath().getLeaf()).isInstanceOf(literalParent);
                                literals.incrementAndGet();
                                return null;
                            }
                        }.scan(new TreePath(unit), null)))
                .isNull();
        assertThat(literals).hasValue(terms);
    }

    @Nullable private static Throwable onASmallStack(final Runnable work) throws InterruptedException {
        final var failure = new AtomicReference<Throwable>();
        final var thread = new Thread(
                null,
                () -> {
                    try {
                        work.run();
                    } catch (final Throwable t) {
                        failure.set(t);
                    }
                },
                "small-stack",
                128 * 1024);
        thread.start();
        thread.join();
        return failure.get();
    }

    private static List<String> javacVisits(final Tree unit) {
        final var visits = new ArrayList<String>();
        new TreeScanner<Void, Void>() {

            @Override
            public Void visitBinary(final BinaryTree node, final Void unused) {
                visits.add("binary " + node.getKind());
                return super.visitBinary(node, null);
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void unused) {
                visits.add("identifier " + node.getName());
                return null;
            }

            @Override
            public Void visitMethodInvocation(final MethodInvocationTree node, final Void unused) {
                visits.add("call");
                return super.visitMethodInvocation(node, null);
            }
        }.scan(unit, null);
        return List.copyOf(visits);
    }

    private static List<String> chainVisits(final Tree unit) {
        final var visits = new ArrayList<String>();
        new ChainScanner<Void, Void>() {

            @Override
            protected void visitBinaryOperator(final BinaryTree node, final Void unused) {
                visits.add("binary " + node.getKind());
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void unused) {
                visits.add("identifier " + node.getName());
                return null;
            }

            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void unused) {
                visits.add("call");
            }
        }.scan(unit, null);
        return List.copyOf(visits);
    }

    private static List<String> javacPaths(final Tree unit) {
        final var paths = new ArrayList<String>();
        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitBinary(final BinaryTree node, final Void unused) {
                paths.add(describe(getCurrentPath()));
                return super.visitBinary(node, null);
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void unused) {
                paths.add(describe(getCurrentPath()));
                return null;
            }

            @Override
            public Void visitMethodInvocation(final MethodInvocationTree node, final Void unused) {
                paths.add(describe(getCurrentPath()));
                return super.visitMethodInvocation(node, null);
            }
        }.scan(new TreePath((com.sun.source.tree.CompilationUnitTree) unit), null);
        return List.copyOf(paths);
    }

    private static List<String> chainPaths(final Tree unit) {
        final var paths = new ArrayList<String>();
        final Consumer<TreePath> record = path -> paths.add(describe(path));
        new ChainPathScanner<Void, Void>() {

            @Override
            protected void visitBinaryOperator(final BinaryTree node, final Void unused) {
                record.accept(getCurrentPath());
            }

            @Override
            public Void visitIdentifier(final IdentifierTree node, final Void unused) {
                record.accept(getCurrentPath());
                return null;
            }

            @Override
            protected void visitInvocation(final MethodInvocationTree node, final Void unused) {
                record.accept(getCurrentPath());
            }
        }.scan(new TreePath((com.sun.source.tree.CompilationUnitTree) unit), null);
        return List.copyOf(paths);
    }

    private static String describe(final TreePath path) {
        return StreamSupport.stream(path.spliterator(), false)
                .map(tree -> tree.getKind().toString())
                .collect(Collectors.joining(" < "));
    }
}
