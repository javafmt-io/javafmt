package io.github.jschneidereit.grind.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DocTest {

    static Stream<Doc> allVariants() {
        return Stream.of(
            new Doc.Text("t"),
            new Doc.Line(),
            new Doc.SoftLine(),
            new Doc.HardLine(),
            new Doc.Indent(new Doc.Text("i")),
            new Doc.Group(new Doc.Text("g")),
            new Doc.Concat(List.of()),
            new Doc.IfBreak(new Doc.Text("b"), new Doc.Text("f")));
    }

    @ParameterizedTest
    @MethodSource("allVariants")
    void everyVariantIsADoc(final Doc doc) {
        assertThat(doc).isInstanceOf(Doc.class);
    }

    @Nested
    class TextTests {

        @Test
        void storesValue() {
            assertThat(new Doc.Text("hello").value()).isEqualTo("hello");
        }

        @Test
        void emptyStringIsValid() {
            assertThat(new Doc.Text("").value()).isEmpty();
        }

        @Test
        void equalityByValue() {
            assertThat(new Doc.Text("abc")).isEqualTo(new Doc.Text("abc"));
        }

        @Test
        void inequalityByValue() {
            assertThat(new Doc.Text("a")).isNotEqualTo(new Doc.Text("b"));
        }

        @Test
        void nullValueThrows() {
            assertThatNullPointerException().isThrownBy(() -> new Doc.Text(null));
        }

        @Test
        void lineFeedInValueThrows() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Doc.Text("foo\nbar"));
        }

        @Test
        void carriageReturnInValueThrows() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Doc.Text("foo\rbar"));
        }
    }

    @Nested
    class ConcatTests {

        @Test
        void storesParts() {
            final var parts = List.<Doc>of(new Doc.Text("a"), new Doc.Text("b"));
            assertThat(new Doc.Concat(parts).parts()).containsExactlyElementsOf(parts);
        }

        @Test
        void emptyPartsIsValid() {
            assertThat(new Doc.Concat(List.of()).parts()).isEmpty();
        }

        @Test
        void equalityByParts() {
            assertThat(new Doc.Concat(List.of(new Doc.Text("x"))))
                .isEqualTo(new Doc.Concat(List.of(new Doc.Text("x"))));
        }

        @Test
        void partsListIsUnmodifiable() {
            assertThat(new Doc.Concat(List.of(new Doc.Text("a"))).parts()).isUnmodifiable();
        }

        @Test
        void mutatingOriginalListDoesNotAffectDoc() {
            final var mutable = new ArrayList<Doc>();
            mutable.add(new Doc.Text("a"));
            final var doc = new Doc.Concat(mutable);
            mutable.add(new Doc.Text("b"));
            assertThat(doc.parts()).hasSize(1);
        }

        @Test
        void nullListThrows() {
            assertThatNullPointerException().isThrownBy(() -> new Doc.Concat((List<Doc>) null));
        }

        @Test
        void streamConstructor_collectsElementsInOrder() {
            final var a = new Doc.Text("a");
            final var b = new Doc.Text("b");
            assertThat(new Doc.Concat(Stream.of(a, b)).parts()).containsExactly(a, b);
        }

        @Test
        void streamConstructor_emptyStream_producesEmptyConcat() {
            assertThat(new Doc.Concat(Stream.<Doc>of()).parts()).isEmpty();
        }
    }

    @Nested
    class IndentTests {

        @Test
        void storesContents() {
            final var inner = new Doc.Text("x");
            assertThat(new Doc.Indent(inner).contents()).isEqualTo(inner);
        }

        @Test
        void equalityByContents() {
            assertThat(new Doc.Indent(new Doc.Text("z")))
                .isEqualTo(new Doc.Indent(new Doc.Text("z")));
        }

        @Test
        void nullContentsThrows() {
            assertThatNullPointerException().isThrownBy(() -> new Doc.Indent(null));
        }
    }

    @Nested
    class LineBreakTests {

        @ParameterizedTest
        @MethodSource("lineVariants")
        void lineVariantsAreEqual(final Doc a, final Doc b) {
            assertThat(a).isEqualTo(b);
        }

        static Stream<Arguments> lineVariants() {
            return Stream.of(
                Arguments.of(new Doc.Line(), new Doc.Line()),
                Arguments.of(new Doc.SoftLine(), new Doc.SoftLine()),
                Arguments.of(new Doc.HardLine(), new Doc.HardLine()));
        }

        @Test
        void lineVariantsAreDistinct() {
            assertThat(new Doc.Line()).isNotEqualTo(new Doc.SoftLine());
            assertThat(new Doc.Line()).isNotEqualTo(new Doc.HardLine());
            assertThat(new Doc.SoftLine()).isNotEqualTo(new Doc.HardLine());
        }
    }

    @Nested
    class GroupTests {

        @Test
        void storesContents() {
            final var inner = new Doc.Text("g");
            assertThat(new Doc.Group(inner).contents()).isEqualTo(inner);
        }

        @Test
        void equalityByContents() {
            assertThat(new Doc.Group(new Doc.Text("g")))
                .isEqualTo(new Doc.Group(new Doc.Text("g")));
        }

        @Test
        void nullContentsThrows() {
            assertThatNullPointerException().isThrownBy(() -> new Doc.Group(null));
        }
    }

    @Nested
    class IfBreakTests {

        @Test
        void storesBreakContents() {
            assertThat(new Doc.IfBreak(new Doc.Text("break"), new Doc.Text("flat")).breakContents())
                .isEqualTo(new Doc.Text("break"));
        }

        @Test
        void storesFlatContents() {
            assertThat(new Doc.IfBreak(new Doc.Text("break"), new Doc.Text("flat")).flatContents())
                .isEqualTo(new Doc.Text("flat"));
        }

        @Test
        void equalityByBothContents() {
            assertThat(new Doc.IfBreak(new Doc.Text("b"), new Doc.Text("f")))
                .isEqualTo(new Doc.IfBreak(new Doc.Text("b"), new Doc.Text("f")));
        }

        @Test
        void nullBreakContentsThrows() {
            assertThatNullPointerException()
                .isThrownBy(() -> new Doc.IfBreak(null, new Doc.Text("f")));
        }

        @Test
        void nullFlatContentsThrows() {
            assertThatNullPointerException()
                .isThrownBy(() -> new Doc.IfBreak(new Doc.Text("b"), null));
        }
    }
}
