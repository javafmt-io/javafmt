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
        final var source = "class Foo { <T extends Comparable<T> & java.io.Serializable> T m(T a, T b) { return a.compareTo(b) > 0 ? a : b; } }";
        assertThatThrownBy(() -> Grind.formatStrict(source))
            .isInstanceOf(UnhandledSyntaxException.class);
    }

    @Test
    void strictThrowsOnColonFormSwitchCase() {
        final var source = "class Foo { void m(int x) { switch (x) { case 1: doIt(); break; default: fallback(); } } void doIt() {} void fallback() {} }";
        assertThatThrownBy(() -> Grind.formatStrict(source))
            .isInstanceOf(UnhandledSyntaxException.class);
    }
}
