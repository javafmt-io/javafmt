package io.github.jschneidereit.grind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jschneidereit.grind.ir.UnhandledSyntaxException;

import org.junit.jupiter.api.Test;

class GrindStrictTest {

    @Test
    void strictReturnsSameOutputWhenEveryNodeHasRenderer() {
        final var source = "class Foo { int x = 1; }";
        assertThat(Grind.formatStrict(source)).isEqualTo(Grind.format(source));
    }

    @Test
    void strictThrowsWhenANodeWouldFallBackToToString() {
        final var source = "class Foo { void m(Object lock) { synchronized (lock) { int x = 1; } } }";
        assertThatThrownBy(() -> Grind.formatStrict(source))
            .isInstanceOf(UnhandledSyntaxException.class);
    }
}
