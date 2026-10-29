package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.CompileCheck;
import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end compile safety for the lint pass: every case here is a shape a lint auto-fix once
 * rewrote into source that no longer compiled, or into a fake parse error from a rolled-back edit.
 * The assertion is differential — output must compile whenever the input did — so snippets javac
 * rejects for unrelated reasons do not have to be excluded by hand.
 */
class LintCompileSafetyTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void formatsToCompilingOutput(final String name, final String source, final boolean analyze) {
        final var result = analyze ? Javafmt.formatWithAnalyze(source, "") : Javafmt.formatWithResult(source);
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
                Arguments.of("diamond", wrap("List<String> xs = new ArrayList<>();\nxs.add(\"a\");", "java.util.ArrayList", "java.util.List"), true),
                Arguments.of("lambda", wrap("Supplier<String> s = () -> \"x\";\ns.get();", "java.util.function.Supplier"), true),
                Arguments.of("method-ref", wrap("Supplier<String> s = String::new;\ns.get();", "java.util.function.Supplier"), true),
                Arguments.of("bare-array", wrap("int[] xs = {1, 2};\nSystem.out.println(xs.length);"), true),
                Arguments.of("boxing-null", wrap("Integer i = 5;\ni = null;\nSystem.out.println(i);"), true),
                Arguments.of("boxing-deref", wrap("Integer i = 5;\nSystem.out.println(i.hashCode());"), true),
                Arguments.of("widening-reassign", """
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
                Arguments.of(
                        "anon-class",
                        wrap(
                                "Object o = new Object() {\n@Override public String toString() { return \"x\"; }\n};\no = \"y\";\nSystem.out.println(o);"),
                        false),
                Arguments.of(
                        "witness-comment",
                        wrap("Optional<String> o = Optional /* a.b */.empty();\nSystem.out.println(o);", "java.util.Optional"),
                        false),
                Arguments.of(
                        "final-field",
                        "class Fixture {\n\n    static final int LIMIT = 0;\n\n    int m() {\n        return LIMIT;\n    }\n}\n",
                        false),
                Arguments.of("interface-field", "interface Fixture {\n\n    int LIMIT = 0;\n}\n", false),
                Arguments.of(
                        "two-imports-one-line",
                        "import java.util.List; import java.util.Set;\n\nclass Fixture {\n\n    List<String> xs() {\n        return List.of();\n    }\n}\n",
                        false),
                Arguments.of("modifier-order-comment", "class Fixture {\n\n    static public /* c */ final int X = 1;\n}\n", false),
                Arguments.of(
                        "modifier-order-sealed",
                        "public class Fixture {\n\n    static public sealed interface I permits R {}\n\n    record R() implements I {}\n}\n",
                        false),
                Arguments.of("mvd-c-style", wrap("int a, b[];\nSystem.out.println(a + b.length);"), false),
                Arguments.of("mvd-plain-local", wrap("int a, b;\nSystem.out.println(a + b);"), false),
                // Three rules used to rewrite a span that every declarator of a group shares, which
                // emitted the same edit once per declarator and made the whole file refuse. Each
                // now waits for MultipleVariableDeclarations to split the group.
                Arguments.of("mvd-interface-field-group", "interface Fixture {\n\n    public static final int A = 1, B = 2;\n}\n", false),
                Arguments.of("mvd-modifier-order-group", "class Fixture {\n\n    static private int a = 1, b = 2;\n}\n", false),
                Arguments.of(
                        "mvd-var-candidate-group",
                        wrap("StringBuilder a = new StringBuilder(), b = new StringBuilder();\nSystem.out.println(a + \"\" + b);"),
                        false),
                Arguments.of(
                        "mvd-var-candidate-group-analyze",
                        wrap("StringBuilder a = new StringBuilder(), b = new StringBuilder();\nSystem.out.println(a + \"\" + b);"),
                        true),
                // A static factory whose receiver happens to share the declared type's simple name
                // says nothing about how many type parameters the method has. Lifting the declared
                // type's arguments onto it as a witness is only sound when the counts agree.
                Arguments.of("factory-witness-arity-two", comparingCase(), false),
                Arguments.of("factory-witness-arity-two-analyze", comparingCase(), true),
                Arguments.of("factory-witness-collector", toMapCase(), false),
                Arguments.of("factory-witness-collector-analyze", toMapCase(), true),
                Arguments.of("factory-witness-map-entry", mapEntryCase(), false),
                Arguments.of(
                        "factory-witness-arity-one",
                        wrap("Optional<String> o = Optional.empty();\nSystem.out.println(o);", "java.util.Optional"),
                        false),
                Arguments.of("factory-witness-list-of", wrap("List<String> xs = List.of();\nSystem.out.println(xs);", "java.util.List"), false),
                // Diamond on an anonymous class is legal only where the inferred type is denotable
                // (JLS 15.9.1). RedundantTypeArguments only fires where the target type's arguments
                // are textually identical to the constructor's, so the inferred type is a type the
                // author already wrote down — these pin that the guard holds for the awkward shapes.
                Arguments.of("anon-diamond-field", ANON_FIELD, false),
                Arguments.of("anon-diamond-field-analyze", ANON_FIELD, true),
                Arguments.of("anon-diamond-return-nested-generic", ANON_RETURN, false),
                Arguments.of("anon-diamond-generic-superclass-with-args", ANON_SUPERCLASS, false),
                Arguments.of("anon-diamond-type-variable-target", ANON_TYPE_VARIABLE, false),
                Arguments.of("anon-diamond-bounded-target", ANON_BOUNDED, false),
                Arguments.of("diamond-wildcard-target", WILDCARD_TARGET, false),
                Arguments.of("diamond-wildcard-target-analyze", WILDCARD_TARGET, true));
    }

    private static final String ANON_FIELD = """
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

    private static final String ANON_RETURN = """
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

    private static final String ANON_SUPERCLASS = """
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

    private static final String ANON_BOUNDED = """
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

    private static final String ANON_TYPE_VARIABLE = """
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
                "Collector<String, ?, Map<String, Integer>> c = Collectors.toMap(s -> s, String::length);\nSystem.out.println(c);",
                "java.util.Map",
                "java.util.stream.Collector",
                "java.util.stream.Collectors");
    }

    private static String mapEntryCase() {
        return wrap("Entry<String, Integer> e = Map.entry(\"a\", 1);\nSystem.out.println(e);", "java.util.Map", "java.util.Map.Entry");
    }

    private static String wrap(final String body, final String... imports) {
        final var importBlock = Stream.of(imports).map(i -> "import " + i + ";\n").reduce("", String::concat);
        return importBlock + "\nclass Fixture {\n\n    void test() {\n        " + body.replace("\n", "\n        ") + "\n    }\n}\n";
    }
}
