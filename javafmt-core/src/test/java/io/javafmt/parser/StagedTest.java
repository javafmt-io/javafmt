package io.javafmt.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StagedTest {

    private static final IllegalStateException FAILURE = new IllegalStateException("stage failed");

    @Test
    void mapAllHandsTheBatchOnlyTheOkValuesAndLinesTheResultsBackUp() {
        final var seen = new AtomicReference<List<String>>();
        final List<Staged<String>> items = List.of(new Staged.Ok<>("a"), new Staged.Failed<>(FAILURE), new Staged.Ok<>("c"));
        final var results = Staged.mapAll(
                items,
                values -> {
                    seen.set(values);
                    return values.stream()
                            .<Staged<Integer>>map(v -> new Staged.Ok<>(v.charAt(0) - 'a'))
                            .toList();
                });
        assertThat(seen.get()).containsExactly("a", "c");
        assertThat(results).containsExactly(new Staged.Ok<>(0), new Staged.Failed<>(FAILURE), new Staged.Ok<>(2));
    }

    @Test
    void mapAllKeepsAFailureTheBatchReturnsForAnOkValue() {
        final List<Staged<String>> items = List.of(new Staged.Ok<>("a"), new Staged.Ok<>("b"));
        final var results = Staged.mapAll(items, values -> List.<Staged<String>>of(new Staged.Ok<>("A"), new Staged.Failed<>(FAILURE)));
        assertThat(results).containsExactly(new Staged.Ok<>("A"), new Staged.Failed<>(FAILURE));
    }

    @Test
    void mapTransformsAnOkValue() {
        assertThat(new Staged.Ok<>(2).map(n -> n * 10)).isEqualTo(new Staged.Ok<>(20));
    }

    @Test
    void mapCarriesAFailureThroughWithoutRunningTheFunction() {
        final Staged<Integer> failed = new Staged.Failed<>(FAILURE);
        assertThat(
                failed.<Integer>map(n -> {
                    throw new AssertionError("must not run on a failure");
                }))
                .isEqualTo(new Staged.Failed<Integer>(FAILURE));
    }

    @Test
    void orElseThrowReturnsAnOkValue() {
        assertThat(new Staged.Ok<>("a").orElseThrow()).isEqualTo("a");
    }

    @Test
    void orElseThrowThrowsTheFailureItself() {
        assertThatThrownBy(() -> new Staged.Failed<String>(FAILURE).orElseThrow()).isSameAs(FAILURE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void mapAllRejectsABatchThatReturnsTheWrongNumberOfResults(final int returned) {
        final List<Staged<String>> items = List.of(new Staged.Ok<>("a"), new Staged.Ok<>("b"));
        assertThatIllegalStateException().isThrownBy(() -> Staged.mapAll(items, values -> Collections.nCopies(returned, new Staged.Ok<>("x"))));
    }
}
