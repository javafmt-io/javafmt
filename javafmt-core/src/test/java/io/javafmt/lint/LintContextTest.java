package io.javafmt.lint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

class LintContextTest {

    @Test
    void parseOnly_factory_hasEmptyAttribution() {
        final var unit = JavaParser.parseUnit("class Foo {}");
        final var ctx = LintContext.parseOnly(unit);
        assertThat(ctx.unit()).isSameAs(unit);
        assertThat(ctx.attribution()).isEmpty();
    }

    @Test
    void attributed_factory_hasPresentAttribution() {
        final var attributed = JavaParser.parseAndAttribute("class Foo {}", "");
        final var ctx = LintContext.attributed(attributed);
        assertThat(ctx.unit()).isSameAs(attributed.unit());
        assertThat(ctx.attribution()).contains(attributed.attribution());
    }

    @Test
    void parseOnly_rejectsNullUnit() {
        assertThatThrownBy(() -> LintContext.parseOnly(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void attributed_rejectsNullAttributedUnit() {
        assertThatThrownBy(() -> LintContext.attributed(null)).isInstanceOf(NullPointerException.class);
    }
}
