package io.javafmt.parser;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface Staged<T> {

    default <R> Staged<R> map(final Function<? super T, ? extends R> f) {
        return switch (this) {
            case Ok<T>(var value) -> new Ok<>(f.apply(value));
            case Failed<T>(var failure) -> new Failed<>(failure);
        };
    }

    default T orElseThrow() {
        return switch (this) {
            case Ok<T>(var value) -> value;
            case Failed<T>(var failure) -> throw failure;
        };
    }

    default Stream<T> stream() {
        return switch (this) {
            case Ok<T>(var value) -> Stream.of(value);
            case Failed<T> ignored -> Stream.empty();
        };
    }

    static <T, R> List<Staged<R>> mapAll(final List<Staged<T>> items, final Function<List<T>, List<Staged<R>>> batch) {
        final var values = items.stream().flatMap(Staged::stream).toList();
        final var batched = batch.apply(values);
        if (batched.size() != values.size()) {
            throw new IllegalStateException("batch returned " + batched.size() + " results for " + values.size() + " values");
        }
        final var results = batched.iterator();
        return items.stream()
                .<Staged<R>>map(
                        item -> switch (item) {
                            case Ok<T> ignored -> results.next();
                            case Failed<T>(var failure) -> new Failed<>(failure);
                        })
                .toList();
    }

    record Ok<T>(T value) implements Staged<T> {

        public Ok {
            Objects.requireNonNull(value, "value");
        }
    }

    record Failed<T>(RuntimeException failure) implements Staged<T> {

        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
