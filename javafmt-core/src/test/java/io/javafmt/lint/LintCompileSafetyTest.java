package io.javafmt.lint;

import io.javafmt.CompileCheck;
import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end compile safety for the lint pass: every case here is a shape a lint auto-fix once
 * rewrote into source that no longer compiled, or into a fake parse error from a rolled-back edit.
 * The assertion is differential — output must compile whenever the input did — so snippets javac
 * rejects for unrelated reasons do not have to be excluded by hand.
 */
class LintCompileSafetyTest {
    @ParameterizedTest(name = "{argumentSetName}")
    @MethodSource("cases")
    void formatsToCompilingOutput(final String source, final boolean analyze, final TestInfo testInfo) {
        final var name = testInfo.getDisplayName();
        final var result = Javafmt.formatWithResult(
                source,
                JavafmtConfig.defaults().withAnalysis(analyze ? new JavafmtConfig.Analysis.Attributed("") : new JavafmtConfig.Analysis.ParseOnly()));
        assertThat(result.diagnostics().stream().filter(d -> d instanceof Diagnostic.InternalError).toList())
                .as("%s must not produce an internal error", name)
                .isEmpty();
        final var before = CompileCheck.check(source);
        final var after = CompileCheck.check(result.output());
        if (before instanceof CompileCheck.Outcome.Compiles) {
            assertThat(after)
                    .as("%s compiled before formatting and must still compile; output:%n%s", name, result.output())
                    .isInstanceOf(CompileCheck.Outcome.Compiles.class);
        }
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                shapesThatOnceStoppedCompiling(), rulesThatRewriteADeclaratorGroupWaitForItToBeSplit(),
                factoriesTakeTheDeclaredTypeArgumentsAsAWitnessOnlyWhenTheTypeParameterCountsAgree(),
                aDiamondReplacesOnlyTypeArgumentsTheAuthorAlreadyWroteOnTheTarget())
                .flatMap(group -> group);
    }

    static Stream<Arguments> shapesThatOnceStoppedCompiling() {
        return Stream.of(
                Arguments.argumentSet(
                        "diamond", wrap("List<String> xs = new ArrayList<>();\nxs.add(\"a\");", "java.util.ArrayList", "java.util.List"), true),
                Arguments.argumentSet("lambda", wrap("Supplier<String> s = () -> \"x\";\ns.get();", "java.util.function.Supplier"), true),
                Arguments.argumentSet(
                        "constructor method reference", wrap("Supplier<String> s = String::new;\ns.get();", "java.util.function.Supplier"), true),
                Arguments.argumentSet("bare-array", wrap("int[] xs = {1, 2};\nSystem.out.println(xs.length);"), true),
                Arguments.argumentSet("boxing-null", wrap("Integer i = 5;\ni = null;\nSystem.out.println(i);"), true),
                Arguments.argumentSet("boxed local dereferenced", wrap("Integer i = 5;\nSystem.out.println(i.hashCode());"), true),
                Arguments.argumentSet("widening-reassign", """
                                                  import java.util.ArrayList;
                                                  import java.util.List;

                                                  class Fixture {

                                                      void test(final List<String> other) {
                                                          List<String> xs = new ArrayList<String>();
                                                          xs = other;
                                                          System.out.println(xs);
                                                      }
                                                  }
                                                  """, true),
                Arguments.argumentSet(
                        "widening-diamond",
                        wrap(
                                "Map<String, List<Integer>> totals = new HashMap<>();\ntotals.put(\"a\", List.of(1));", "java.util.HashMap",
                                "java.util.List", "java.util.Map"),
                        false),
                Arguments.argumentSet(
                        "subtype-feeds-chain-inference",
                        wrap(
                                "Collection<String> names = List.of(\"a\");\nList<Collection<String>> rows = Stream.of(names).toList();\nSystem.out.println(rows);",
                                "java.util.Collection", "java.util.List", "java.util.stream.Stream"),
                        true),
                Arguments.argumentSet(
                        "anonymous-class",
                        wrap(
                                "Object o = new Object() {\n@Override public String toString() { return \"x\"; }\n};\no = \"y\";\nSystem.out.println(o);"),
                        false),
                Arguments.argumentSet(
                        "witness-comment", wrap("Optional<String> o = Optional /* a.b */.empty();\nSystem.out.println(o);", "java.util.Optional"),
                        false),
                Arguments.argumentSet(
                        "final-field", "class Fixture {\n    static final int LIMIT = 0;\n\n    int m() {\n        return LIMIT;\n    }\n}\n", false),
                Arguments.argumentSet("interface-field", "interface Fixture {\n    int LIMIT = 0;\n}\n", false),
                Arguments.argumentSet(
                        "two-imports-one-line",
                        "import java.util.List; import java.util.Set;\n\nclass Fixture {\n    List<String> xs() {\n        return List.of();\n    }\n}\n",
                        false),
                Arguments.argumentSet("modifier-order-comment", "class Fixture {\n    static public /* c */ final int X = 1;\n}\n", false),
                Arguments.argumentSet(
                        "modifier-order-sealed",
                        "public class Fixture {\n    static public sealed interface I permits R {}\n\n    record R() implements I {}\n}\n", false),
                Arguments.argumentSet(
                        "multiple variable declarations with a C-style array", wrap("int a, b[];\nSystem.out.println(a + b.length);"), false),
                Arguments.argumentSet("multiple variable declarations of plain locals", wrap("int a, b;\nSystem.out.println(a + b);"), false));
    }

    static Stream<Arguments> rulesThatRewriteADeclaratorGroupWaitForItToBeSplit() {
        return Stream.of(
                Arguments.argumentSet(
                        "a redundant modifier on an interface field group is removed once, after the group is split",
                        "interface Fixture {\n    public static final int A = 1, B = 2;\n}\n", false),
                Arguments.argumentSet(
                        "the modifiers of a field group are reordered once, after the group is split",
                        "class Fixture {\n    static private int a = 1, b = 2;\n}\n", false),
                Arguments.argumentSet(
                        "a local group becomes var once, after the group is split",
                        wrap("StringBuilder a = new StringBuilder(), b = new StringBuilder();\nSystem.out.println(a + \"\" + b);"), false),
                Arguments.argumentSet(
                        "a local group becomes var once, after the group is split, with analysis",
                        wrap("StringBuilder a = new StringBuilder(), b = new StringBuilder();\nSystem.out.println(a + \"\" + b);"), true));
    }

    static Stream<Arguments> factoriesTakeTheDeclaredTypeArgumentsAsAWitnessOnlyWhenTheTypeParameterCountsAgree() {
        return Stream.of(
                Arguments.argumentSet("a factory with two type parameters on a receiver named like the declared type", comparingCase(), false),
                Arguments.argumentSet(
                        "a factory with two type parameters on a receiver named like the declared type, with analysis", comparingCase(), true),
                Arguments.argumentSet("a collector factory with more type parameters than the declared type", toMapCase(), false),
                Arguments.argumentSet("a collector factory with more type parameters than the declared type, with analysis", toMapCase(), true),
                Arguments.argumentSet("a map entry factory on the enclosing type of the declared type", mapEntryCase(), false),
                Arguments.argumentSet(
                        "a factory with one type parameter for a declared type with one",
                        wrap("Optional<String> o = Optional.empty();\nSystem.out.println(o);", "java.util.Optional"), false),
                Arguments.argumentSet(
                        "a list factory for a declared list", wrap("List<String> xs = List.of();\nSystem.out.println(xs);", "java.util.List"),
                        false));
    }

    static Stream<Arguments> aDiamondReplacesOnlyTypeArgumentsTheAuthorAlreadyWroteOnTheTarget() {
        return Stream.of(
                Arguments.argumentSet("an anonymous class assigned to a field", ANONYMOUS_FIELD, false),
                Arguments.argumentSet("an anonymous class assigned to a field, with analysis", ANONYMOUS_FIELD, true),
                Arguments.argumentSet("an anonymous class returned as a nested generic type", ANONYMOUS_RETURN, false),
                Arguments.argumentSet("an anonymous subclass of a generic class with constructor arguments", ANONYMOUS_SUPERCLASS, false),
                Arguments.argumentSet("an anonymous class whose target type argument is a type variable", ANONYMOUS_TYPE_VARIABLE, false),
                Arguments.argumentSet("an anonymous class whose type parameter is bounded", ANONYMOUS_BOUNDED, false),
                Arguments.argumentSet("a wildcard target type", WILDCARD_TARGET, false),
                Arguments.argumentSet("a wildcard target type, with analysis", WILDCARD_TARGET, true));
    }

    private static final String ANONYMOUS_FIELD = """
            import java.util.Comparator;

            class Fixture {

                Comparator<String> byLength = new Comparator<String>() {

                    @Override
                    public int compare(String a, String b) {
                        return a.length() - b.length();
                    }
                };
            }
            """;

    private static final String ANONYMOUS_RETURN = """
            import java.util.List;
            import java.util.concurrent.Callable;

            class Fixture {

                Callable<List<String>> supplier() {
                    return new Callable<List<String>>() {

                        @Override
                        public List<String> call() {
                            return List.of();
                        }
                    };
                }
            }
            """;

    private static final String ANONYMOUS_SUPERCLASS = """
            class Fixture {

                abstract static class Box<T> {

                    Box(T seed) {}

                    abstract T get();
                }

                Box<Number> boxed() {
                    return new Box<Number>(1) {

                        @Override
                        Number get() {
                            return 1;
                        }
                    };
                }
            }
            """;

    private static final String ANONYMOUS_BOUNDED = """
            class Fixture {

                interface Holder<T extends Comparable<T>> {

                    T value();
                }

                Holder<String> held() {
                    return new Holder<String>() {

                        @Override
                        public String value() {
                            return "x";
                        }
                    };
                }
            }
            """;

    private static final String ANONYMOUS_TYPE_VARIABLE = """
            import java.util.function.Supplier;

            class Fixture {

                static <T> Supplier<T> constant(T value) {
                    return new Supplier<T>() {

                        @Override
                        public T get() {
                            return value;
                        }
                    };
                }
            }
            """;

    private static final String WILDCARD_TARGET = """
            import java.util.ArrayList;
            import java.util.List;

            class Fixture {

                void test() {
                    List<? extends Number> xs = new ArrayList<Number>();
                    System.out.println(xs);
                }
            }
            """;

    private static String comparingCase() {
        return wrap(
                "Comparator<String> c = Comparator.comparing(String::length);\nSystem.out.println(c.compare(\"a\", \"bb\"));",
                "java.util.Comparator");
    }

    private static String toMapCase() {
        return wrap(
                "Collector<String, ?, Map<String, Integer>> c = Collectors.toMap(s -> s, String::length);\nSystem.out.println(c);", "java.util.Map",
                "java.util.stream.Collector", "java.util.stream.Collectors");
    }

    private static String mapEntryCase() {
        return wrap("Entry<String, Integer> e = Map.entry(\"a\", 1);\nSystem.out.println(e);", "java.util.Map", "java.util.Map.Entry");
    }

    private static String wrap(final String body, final String... imports) {
        final var importBlock = Stream.of(imports).map(i -> "import " + i + ";\n").reduce("", String::concat);
        return importBlock + "\nclass Fixture {\n    void test() {\n        " + body.replace("\n", "\n        ") + "\n    }\n}\n";
    }
}
