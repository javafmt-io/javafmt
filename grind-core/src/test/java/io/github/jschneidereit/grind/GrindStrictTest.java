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
        final var source = "class Foo { int m(int x) { return switch (x) { case 1 -> { yield 42; } default -> 0; }; } }";
        assertThatThrownBy(() -> Grind.formatStrict(source))
            .isInstanceOf(UnhandledSyntaxException.class);
    }
}
