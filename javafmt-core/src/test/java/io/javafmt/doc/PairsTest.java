package io.javafmt.doc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PairsTest {

    static Stream<Arguments> pairwiseCases() {
        return Stream.of(
                Arguments.of(List.of(), List.of()),
                Arguments.of(List.of("a"), List.of()),
                Arguments.of(List.of("a", "b"), List.of("ab")),
                Arguments.of(List.of("a", "b", "c", "d"), List.of("ab", "bc", "cd")));
    }

    @ParameterizedTest
    @MethodSource("pairwiseCases")
    void pairwiseCombinesEachElementWithItsPredecessor(final List<String> input, final List<String> expected) {
        assertThat(
                input.stream()
                        .gather(Pairs.<String, String>pairwise((previous, current) -> previous + current))
                        .toList())
                .isEqualTo(expected);
    }

    static Stream<Arguments> withPreviousCases() {
        return Stream.of(
                Arguments.of(List.of(), List.of()),
                Arguments.of(List.of("a"), List.of("-a")),
                Arguments.of(List.of("a", "b", "c"), List.of("-a", "ab", "bc")));
    }

    @ParameterizedTest
    @MethodSource("withPreviousCases")
    void withPreviousHandsTheFirstElementAnEmptyPredecessor(final List<String> input, final List<String> expected) {
        assertThat(
                input.stream()
                        .gather(Pairs.<String, String>withPrevious((previous, current) -> previous.orElse("-") + current))
                        .toList())
                .isEqualTo(expected);
    }

    @Test
    void withPreviousSeesAnEmptyOptionalOnlyForTheFirstElement() {
        final var seen = Stream.of("a", "b", "c")
                .gather(Pairs.<String, Optional<String>>withPrevious((previous, _) -> previous))
                .toList();
        assertThat(seen).containsExactly(Optional.empty(), Optional.of("a"), Optional.of("b"));
    }

    @Test
    void pairwiseCanExpandEachPairIntoAStream() {
        final var flattened = Stream.of(1, 2, 3)
                .gather(Pairs.<Integer, Stream<Integer>>pairwise((previous, current) -> Stream.of(previous, current * 10)))
                .flatMap(stream -> stream)
                .toList();
        assertThat(flattened).containsExactly(1, 20, 2, 30);
    }

    @Test
    void aGathererIsReusableAcrossStreams() {
        final var gatherer = Pairs.<String, String>pairwise((previous, current) -> previous + current);
        assertThat(Stream.of("a", "b").gather(gatherer).toList()).containsExactly("ab");
        assertThat(Stream.of("x", "y").gather(gatherer).toList()).containsExactly("xy");
    }

    @Test
    void rejectsANullCombiner() {
        assertThatNullPointerException().isThrownBy(() -> Pairs.pairwise(null));
        assertThatNullPointerException().isThrownBy(() -> Pairs.withPrevious(null));
    }
}
