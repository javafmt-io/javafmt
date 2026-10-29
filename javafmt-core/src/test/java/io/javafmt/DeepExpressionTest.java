package io.javafmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DeepExpressionTest {

    private static final int SEARCH_CEILING = 1 << 17;

    @TempDir
    private static Path classes;

    private static Map<Chain, Integer> deepestCompilable = Map.of();

    @BeforeAll
    static void findTheDeepestChainsJavacCompiles() {
        deepestCompilable = Arrays.stream(Chain.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), DeepExpressionTest::searchForTheDeepestCompilable));
    }

    @ParameterizedTest
    @EnumSource(Chain.class)
    void formatsTheDeepestChainJavacCanCompile(final Chain chain) {
        final var source = chain.of(deepestCompilable.get(chain));
        final var result = Javafmt.formatWithResult(source, JavafmtConfig.defaults());
        assertThat(result.diagnostics()).isEmpty();
        assertThat(Javafmt.format(result.output())).isEqualTo(result.output());
    }

    @ParameterizedTest
    @EnumSource(Chain.class)
    void formatsTheDeepestChainJavacCanCompileInABatch(final Chain chain) {
        final var sources = List.of("class Before {}\n", chain.of(deepestCompilable.get(chain)), "class After {}\n");
        final var results = Javafmt.formatAll(sources, JavafmtConfig.defaults(), 2);
        assertThat(results).allSatisfy(result -> assertThat(result.diagnostics()).isEmpty());
        assertThat(results.get(1).output()).isEqualTo(Javafmt.format(sources.get(1)));
    }

    private static int searchForTheDeepestCompilable(final Chain chain) {
        final var deepest = largestPassing(terms -> javacCompiles(chain.of(terms)));
        assertThat(deepest).as("javac compiles at least a short chain").isGreaterThan(1);
        return deepest;
    }

    private static boolean javacCompiles(final String source) {
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var file = new SimpleJavaFileObject(URI.create("string:///Deep.java"), JavaFileObject.Kind.SOURCE) {

            @Override
            public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try {
            return compiler.getTask(null, null, diagnostic -> {}, List.of("-proc:none", "-d", classes.toString()), null, List.of(file)).call();
        } catch (final RuntimeException | StackOverflowError e) {
            return false;
        }
    }

    private static int largestPassing(final IntPredicate passes) {
        final var failing = IntStream.iterate(2, n -> n <= SEARCH_CEILING, n -> n * 2)
                .filter(n -> !passes.test(n))
                .findFirst()
                .orElse(SEARCH_CEILING);
        return bisect(failing / 2, failing, passes);
    }

    private static int bisect(final int passing, final int failing, final IntPredicate passes) {
        if (failing - passing <= 1) {
            return passing;
        }
        final var middle = passing + (failing - passing) / 2;
        return passes.test(middle) ? bisect(middle, failing, passes) : bisect(passing, middle, passes);
    }

    enum Chain {

        ALTERNATING("Deep", "a", ".f().a", ""),
        ARITHMETIC("int", "1", " + 1", ""),
        FIELD_SELECT("Deep", "a", ".a", ""),
        LOGICAL("boolean", "true", " && true", ""),
        METHOD_CHAIN("String", "new StringBuilder()", ".append(1)", ".toString()");

        private final String type;

        private final String prefix;

        private final String link;

        private final String suffix;

        Chain(final String type, final String prefix, final String link, final String suffix) {
            this.type = type;
            this.prefix = prefix;
            this.link = link;
            this.suffix = suffix;
        }

        String of(final int links) {
            return """
                    class Deep {

                        Deep a;

                        Deep f() {
                            return this;
                        }

                        %s value = %s;
                    }
                    """.formatted(type, prefix + link.repeat(links) + suffix);
        }
    }
}
