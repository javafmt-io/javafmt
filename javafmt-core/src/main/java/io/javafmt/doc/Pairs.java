package io.javafmt.doc;

import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Gatherer;

@UtilityClass
public final class Pairs {

    public <T, R> Gatherer<T, ?, R> pairwise(final BiFunction<? super T, ? super T, ? extends R> combine) {
        Objects.requireNonNull(combine, "combine");
        return Gatherer.<T, Last<T>, R>ofSequential(Last::new, (last, element, downstream) -> {
            final var accepted = last.value.map(previous -> downstream.push(combine.apply(previous, element)))
                    .orElse(true);
            last.value = Optional.of(element);
            return accepted;
        });
    }

    public <T, R> Gatherer<T, ?, R> withPrevious(final BiFunction<? super Optional<T>, ? super T, ? extends R> combine) {
        Objects.requireNonNull(combine, "combine");
        return Gatherer.<T, Last<T>, R>ofSequential(Last::new, (last, element, downstream) -> {
            final var accepted = downstream.push(combine.apply(last.value, element));
            last.value = Optional.of(element);
            return accepted;
        });
    }

    private static final class Last<T> {

        private Optional<T> value = Optional.empty();
    }
}
