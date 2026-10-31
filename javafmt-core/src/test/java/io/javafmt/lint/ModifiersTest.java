package io.javafmt.lint;

import io.javafmt.JavaModifiers;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ModifiersTest {
    @Test
    void canonicalOrderIncludesSealedModifiers() {
        assertThat(JavaModifiers.JLS_ORDER).contains(Modifier.SEALED, Modifier.NON_SEALED);
        assertThat(JavaModifiers.JLS_ORDER).startsWith(Modifier.PUBLIC, Modifier.PROTECTED, Modifier.PRIVATE);
    }
}
