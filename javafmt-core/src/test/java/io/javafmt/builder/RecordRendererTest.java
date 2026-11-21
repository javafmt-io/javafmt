package io.javafmt.builder;

import io.javafmt.Diagnostic;
import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import io.javafmt.parser.JavaParser;
import io.javafmt.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.javafmt.builder.RendererTestSupport.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordRendererTest {

    private static final String COMPACT_CONSTRUCTOR_WITH_THROWS = "record R(int x) {\n\n    R throws Exception {}\n}\n";

    static Stream<JavafmtConfig> analyses() {
        return Stream.of(JavafmtConfig.defaults(), JavafmtConfig.defaults().withAnalysis(new JavafmtConfig.Analysis.Attributed("")));
    }

    @ParameterizedTest
    @MethodSource("analyses")
    void aCompactConstructorWithAThrowsClauseIsRefusedAsAParseError(final JavafmtConfig config) {
        final var result = Javafmt.formatWithResult(COMPACT_CONSTRUCTOR_WITH_THROWS, config);
        assertThat(result.output()).isEqualTo(COMPACT_CONSTRUCTOR_WITH_THROWS);
        assertThat(result.diagnostics())
                .singleElement()
                .isInstanceOf(Diagnostic.ParseError.class)
                .extracting(Diagnostic::message)
                .asString()
                .contains("throws clause not allowed");
    }

    @Test
    void theParserRefusesACompactConstructorWithAThrowsClauseSoItNeverReachesTheRenderer() {
        assertThatThrownBy(() -> JavaParser.parseUnit(COMPACT_CONSTRUCTOR_WITH_THROWS))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void emptyRecord_rendersOnOneLine() {
        assertThat(format("record Empty() {}")).isEqualTo("record Empty() {}");
    }

    @Test
    void recordWithComponents_fitsOnOneLine() {
        assertThat(format("record Pair(int x, int y) {}")).isEqualTo("record Pair(int x, int y) {}");
    }

    @Test
    void recordWithComponentsAndBodyMember_componentListStaysOnOneLine() {
        assertThat(format("record R(int x) { void go() {} }")).isEqualTo("record R(int x) {\n\n    void go() {}\n}");
    }

    @Test
    void record_withImplements_rendersAfterComponents() {
        assertThat(format("record R(int x) implements Comparable<R> {}")).isEqualTo("record R(int x) implements Comparable<R> {}");
    }

    @Test
    void record_withNestedClass_stillRenders() {
        assertThat(format("record R(int x) { class Helper {} }")).isEqualTo("record R(int x) {\n\n    class Helper {}\n}");
    }

    @Test
    void recordWithManyComponents_breaksToOnePerLine() {
        final var source = "record LongRecord(FirstVeryLongComponentType firstVeryLongComponentName, SecondVeryLongComponentType secondVeryLongComponent, ThirdVeryLong thirdVeryLong) {}";
        final var expected = """
                record LongRecord(FirstVeryLongComponentType firstVeryLongComponentName,
                                  SecondVeryLongComponentType secondVeryLongComponent,
                                  ThirdVeryLong thirdVeryLong) {}""";
        assertThat(format(source)).isEqualTo(expected);
    }

    @Test
    void recordWithParameterizedComponentTypes_rendersTypesThroughDocIr() {
        // Component types must travel through recursor.scan, not Tree.toString — the latter
        // bypasses the Doc IR and would crash Doc.Text on any \n a future pretty-printer
        // emits. Nested generics + wildcards exercise the recursive scan path.
        assertThat(format("record R(java.util.Map<String, java.util.List<? extends Number>> data) {}"))
                .isEqualTo("record R(java.util.Map<String, java.util.List<? extends Number>> data) {}");
    }
}
