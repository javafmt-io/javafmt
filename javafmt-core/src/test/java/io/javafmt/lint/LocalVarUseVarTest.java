package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalVarUseVarTest {

    private static String rewritten(final String body) {
        final var unit = JavaParser.parseUnit("""
                import java.util.ArrayList;
                import java.util.Collection;
                import java.util.Collections;
                import java.util.Comparator;
                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.function.Function;

                class Invoices {

                    void settle(final Object amount, final List<String> others) {
                %s
                    }
                }
                """.formatted(body));
        return LintEngine.applyEdits(unit.source(), new LocalVarUseVar().apply(LintContext.parseOnly(unit)).edits());
    }

    @ParameterizedTest
    @CsvSource( delimiter = '|', value = { "ArrayList<String> lines = new ArrayList<>();         | var lines = new ArrayList<String>();", "HashMap<String, List<Integer>> totals = new HashMap<>(); | var totals = new HashMap<String, List<Integer>>();", "Object total = (Object) amount;                      | var total = (Object) amount;", "Map<String,Integer> totals = (Map<String, Integer>) amount; | var totals = (Map<String, Integer>) amount;","String currency = \"EUR\";                           | var currency = \"EUR\";", "int count = 1;                                       | var count = 1;", "long cents = 1L;                                     | var cents = 1L;", "boolean paid = true;                                 | var paid = true;", "char grade = 'A';                                    | var grade = 'A';", })
    void aLocalWhoseTypeTheInitializerShowsBecomesVar(final String declaration, final String expected) {
        assertThat(rewritten(declaration)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource( strings = { "Runnable task = () -> {};", "Function<String, Integer> length = String::length;", "String note = null;", "int[] slots = {1};", "Comparator<String> order = new Comparator<>() { public int compare(String a, String b) { return 0; } };", "long cents = 1;", "float rate = 1;", "char grade = 65;", "Integer count = 5;", "Box<String> box = Box.<String>sealed();", "Box<String> box = Box.<String>raw();", "Collection<String> lines = List.<String>of();", "List<String> lines = Collections.<String>emptyList();", "Object label = amount == null ? \"a\" : \"b\";", "List<String> lines = amount == null ? new ArrayList<>() : new ArrayList<>();", "List<String> lines = new ArrayList<String>();", "List<String> lines = new ArrayList<>();", "Map<String, List<Integer>> totals = new HashMap<>();", "Collection<Integer> ids = new ArrayList<>(List.of(5, 7, 1)); ids.remove(1);", "Object total = new StringBuilder();", "String label = switch (others.size()) { case 1 -> \"one\"; default -> \"many\"; };", "List<? extends Number> amounts = new ArrayList<>();", "List<String> lines = new ArrayList();", "List lines = new ArrayList<String>();", "Map<String, Integer> totals = new LedgerMap<>();", "Object total = (String) amount;", "@Deprecated List<String> lines = new ArrayList<String>();", "List<String> lines = new ArrayList<String>(); lines = others;", "String currency = \"EUR\"; currency = \"USD\";", "int count = 1; count++;", "Object total = (Object) amount; total = others;", })
    void aLocalWhoseTypeCannotBeProvenKeepsIt(final String declaration) {
        assertThat(rewritten(declaration)).contains(declaration).doesNotContain("var ");
    }

    @Test
    void explicitTypeReplacedWithVarForNewExpression() {
        final var unit = JavaParser.parseUnit("""
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  void test() {
                                                      ArrayList<String> list = new ArrayList<String>();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("var");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void alreadyVarTypeProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  void test() {
                                                      var list = new ArrayList<String>();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void noInitializerProducesNoEdits() {
        final var unit = JavaParser.parseUnit("""
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  void test(final ArrayList<String> list) {
                                                      ArrayList<String> copy;
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void fieldNotReplaced() {
        // Only locals, not fields
        final var unit = JavaParser.parseUnit("""
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  ArrayList<String> field = new ArrayList<String>();
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void staticFactoryWithMatchingReceiverIsLeftAloneWithoutAttribution() {
        // Lifting Optional<Path>'s type argument onto the call needs to know that Optional.empty()
        // declares exactly one type parameter. Parse-only cannot know that, and guessing produced
        // source that did not compile for factories such as Comparator.comparing; see
        // LocalVarUseVarAnalyzeTest for the rewrite this becomes once attribution is available.
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Optional;
                                              import java.nio.file.Path;
                                              class Fixture {

                                                  void test() {
                                                      Optional<Path> cacheFile = Optional.empty();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void staticFactoryWithMismatchedReceiverNotReplaced() {
        // List<Path> x = Collections.emptyList() — receiver Collections does not match the
        // declared raw type List, so we can't safely synthesize a type witness.
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Collections;
                                              import java.util.List;
                                              import java.nio.file.Path;
                                              class Fixture {

                                                  void test() {
                                                      List<Path> files = Collections.emptyList();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void staticFactoryWithMatchingExistingWitnessIsLeftAloneWithoutAttribution() {
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Collections;
                                              import java.util.List;
                                              import java.nio.file.Path;
                                              class Fixture {

                                                  void test() {
                                                      List<Path> files = List.<Path>of();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        final var rewritten = LintEngine.applyEdits(unit.source(), result.edits());
        assertThat(rewritten).contains("List<Path> files = List.<Path>of();").doesNotContain("var ");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void staticFactoryWithMismatchedExistingWitnessNotTouched() {
        // Declared args differ from the witness args (widening to a wildcard).
        // `var` would infer the narrower call type, changing the variable's apparent type.
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Optional;
                                              class Fixture {

                                                  void test() {
                                                      Optional<? extends Number> opt = Optional.<Integer>of(5);
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void rawDeclaredTypeOnMethodCallNotReplaced() {
        // Optional x = Optional.empty() — no type arguments to lift, so there's nothing
        // for the type witness to carry. Leave it alone.
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Optional;
                                              class Fixture {

                                                  void test() {
                                                      Optional opt = Optional.empty();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void simpleTypeNoGenericsReplaced() {
        final var unit = JavaParser.parseUnit("""
                                              class Fixture {

                                                  void test() {
                                                      StringBuilder sb = new StringBuilder();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).hasSize(1);
        assertThat(result.edits().getFirst().replacement()).isEqualTo("var");
    }

    @Test
    void anonymousClassInitializerIsNotRewritten() {
        // `var o = new Object() {...}` types o as the anonymous class, so a later `o = "y"` — or
        // any other use of the declared supertype — stops compiling.
        final var unit = JavaParser.parseUnit("""
            class Fixture {

                void test() {
                    Object o = new Object() {

                        @Override
                        public String toString() {
                            return "x";
                        }
                    };
                    o = "y";
                    System.out.println(o);
                }
            }
            """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void aDeclarationGroupIsNeverRewrittenToVar() {
        // `var` is illegal in a compound declaration, and the shared type span made the rule emit
        // the same edit once per declarator, which applyEdits rejected as overlapping.
        // MultipleVariableDeclarations splits the group first and each declaration is then its own
        // candidate. java/lang/invoke/ClassSpecializer.java was refused over this.
        final var unit = JavaParser.parseUnit("""
                                              class Fixture {

                                                  static class Var {}

                                                  void test() {
                                                      Var a = new Var(), b = new Var();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }
}
