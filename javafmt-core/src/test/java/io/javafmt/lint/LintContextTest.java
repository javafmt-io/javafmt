package io.javafmt.lint;

import io.javafmt.parser.JavaParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LintContextTest {
    @Test
    void parseOnly_factory_hasEmptyAttribution() {
        final var unit = JavaParser.parseUnit("class Foo {}");
        final var context = LintContext.parseOnly(unit);
        assertThat(context.unit()).isSameAs(unit);
        assertThat(context.attribution()).isEmpty();
    }

    @Test
    void attributed_factory_hasPresentAttribution() {
        JavaParser.attributing(
                "",
                session -> {
                    final var attributed = session.attribute("class Foo {}");
                    final var context = LintContext.attributed(attributed);
                    assertThat(context.unit()).isSameAs(attributed.unit());
                    assertThat(context.attribution()).contains(attributed.attribution());
                    return context;
                });
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
