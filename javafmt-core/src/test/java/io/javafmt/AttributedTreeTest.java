package io.javafmt;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttributedTreeTest {

    private static final JavafmtConfig ANALYZE = JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed(""));

    @ParameterizedTest
    @ValueSource(strings = {"""
            class Matcher<T extends Missing> {
                void match(final T target) {
                    for (final Object element : target) {
                        System.out.println(element);
                    }
                }
            }
            """, """
            import java.util.ArrayList;

            class Ledger {
                void record(final String name) {
                    final var translation = new StringBuilder();
                    final var names = new ArrayList<String>();
                    final var missing = new Unknown();
                    final var values = new int[3];
                    final var count = 1;
                    translation.append(name).append(names).append(missing).append(values.length).append(count);
                }
            }
            """, """
            enum Format {
                LOWER("-") {
                    @Override
                    String join(String word) {
                        return word;
                    }
                };

                private final String separator;

                Format(String separator) {
                    this.separator = separator;
                }

                abstract String join(String word);
            }
            """, """
            abstract class Catalog {
                abstract int size();

                Object reversed() {
                    return new Reversed();
                }

                private final class Reversed {
                    int size() {
                        return Catalog.this.size();
                    }
                }
            }
            """, """
            class Shelf {
                class Item {}

                void stock() {}
            }
            """, """
            record Parcel(String label, int weight) {
                static class Box {}

                Parcel {
                    label = label.strip();
                }

                String describe() {
                    return label + weight;
                }
            }
            """, """
            class Basket {
                Object wrap(String item) {
                    return new Object() {
                        @Override
                        public String toString() {
                            return item;
                        }
                    };
                }
            }
            """})
    void codeJavacAddsMembersToFormatsAsItDoesWithoutAClasspath(final String source) {
        final var attributed = Javafmt.formatWithResult(source, ANALYZE);
        assertThat(attributed.diagnostics()).isEmpty();
        assertThat(attributed).isEqualTo(Javafmt.formatWithResult(source));
    }

    @Test
    void aModuleDeclarationIsFormattedWithoutTheClasspathAndLeavesTheOtherFilesOfItsBatchOnTheClasspath() {
        final var module = "module orders {\n    exports shop;\n}\n";
        final var store = "package shop;\n\nimport java.sql.*;\n\npublic class Store {\n    Connection connection;\n}\n";
        final var results = Javafmt.formatAll(List.of(module, store), ANALYZE, 1);
        assertThat(results.getFirst()).isEqualTo(Javafmt.formatWithResult(module));
        assertThat(results.get(1).output())
                .contains("import java.sql.Connection;")
                .doesNotContain("java.sql.*");
    }

    @Test
    void aFileTheAttributedPassFailsOnIsFormattedWithoutTheClasspathWithAWarning() {
        final var source = "class Order {   }\n";
        final var failed = new FormatResult(source, List.of(new Diagnostic.InternalError("boom", new Position.Unknown())));
        final var results = BatchFormatter.retriedWithoutClasspath(List.of(source), List.of(failed), ANALYZE, BatchFormatter.Renderer.INLINE);
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.output()).isEqualTo("class Order {}\n");
            assertThat(result.diagnostics())
                    .singleElement()
                    .isInstanceOf(Diagnostic.Warning.class)
                    .extracting(Diagnostic::message, InstanceOfAssertFactories.STRING)
                    .contains("boom", "without the rules that need resolved types");
        });
    }

    @Test
    void aParseErrorUnderAClasspathIsNotRetried() {
        final var source = "class Order {";
        final var failed = new FormatResult(source, List.of(new Diagnostic.ParseError("broken", new Position.Unknown())));
        assertThat(BatchFormatter.retriedWithoutClasspath(List.of(source), List.of(failed), ANALYZE, BatchFormatter.Renderer.INLINE))
                .containsExactly(failed);
    }
}
