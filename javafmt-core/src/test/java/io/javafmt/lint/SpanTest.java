package io.javafmt.lint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpanTest {

    @Test
    void coversTheStartButNotTheEnd() {
        final var span = new Span(2, 5);
        assertThat(span.covers(1)).isFalse();
        assertThat(span.covers(2)).isTrue();
        assertThat(span.covers(4)).isTrue();
        assertThat(span.covers(5)).isFalse();
    }

    @Test
    void overlapsOnlyWhenTheyShareAnOffset() {
        final var span = new Span(2, 5);
        assertThat(span.overlaps(new Span(4, 8))).isTrue();
        assertThat(span.overlaps(new Span(0, 3))).isTrue();
        assertThat(span.overlaps(new Span(5, 8))).isFalse();
        assertThat(span.overlaps(new Span(0, 2))).isFalse();
    }
}
