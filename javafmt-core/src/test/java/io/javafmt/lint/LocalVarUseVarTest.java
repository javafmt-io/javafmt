package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

class LocalVarUseVarTest {

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
    void widenedTypeNotReplaced() {
        // List<String> x = new ArrayList<String>() — declared type is wider, keep it
        final var unit = JavaParser.parseUnit("""
                                              import java.util.List;
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  void test() {
                                                      List<String> list = new ArrayList<String>();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void diamondInferenceNotReplaced() {
        // ArrayList<String> x = new ArrayList<>() — diamond inference changes semantics
        final var unit = JavaParser.parseUnit("""
                                              import java.util.ArrayList;
                                              class Fixture {

                                                  void test() {
                                                      ArrayList<String> list = new ArrayList<>();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
    }

    @Test
    void primitiveTypeNotReplaced() {
        final var unit = JavaParser.parseUnit("""
                                              class Fixture {

                                                  void test() {
                                                      int x = 0;
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        assertThat(result.edits()).isEmpty();
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
    void staticFactoryWithMatchingExistingWitnessReplacesLhs() {
        // The call already carries the type — dropping the LHS for `var` is trivial.
        // This also picks up Collections.<T>emptyList()-style calls that the synthesis
        // path's receiver heuristic would otherwise reject.
        final var unit = JavaParser.parseUnit("""
                                              import java.util.Collections;
                                              import java.util.List;
                                              import java.nio.file.Path;
                                              class Fixture {

                                                  void test() {
                                                      List<Path> files = Collections.<Path>emptyList();
                                                  }
                                              }
                                              """);
        final var result = new LocalVarUseVar().apply(LintContext.parseOnly(unit));
        final var rewritten = LintEngine.applyEdits(unit.source(), result.edits());
        assertThat(rewritten).contains("var files = Collections.<Path>emptyList();");
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
