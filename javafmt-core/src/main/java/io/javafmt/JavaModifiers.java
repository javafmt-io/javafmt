package io.javafmt;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import lombok.experimental.UtilityClass;

/**
 * The Java Language Specification's canonical modifier order, shared by
 * {@code io.javafmt.builder.ModifierRenderer} (which prints modifiers on formatted output) and
 * {@code io.javafmt.lint.ModifierOrder} (which detects and reorders modifiers written out of
 * order in the source). A single list means the two can never drift apart on what "canonical"
 * means.
 */
@UtilityClass
public final class JavaModifiers {

    public final List<Modifier> JLS_ORDER = List.of(
            Modifier.PUBLIC,
            Modifier.PROTECTED,
            Modifier.PRIVATE,
            Modifier.ABSTRACT,
            Modifier.DEFAULT,
            Modifier.STATIC,
            Modifier.FINAL,
            Modifier.SEALED,
            Modifier.NON_SEALED,
            Modifier.TRANSIENT,
            Modifier.VOLATILE,
            Modifier.SYNCHRONIZED,
            Modifier.NATIVE,
            Modifier.STRICTFP);

    public final Set<Modifier> KNOWN_MODIFIERS = Collections.unmodifiableSet(EnumSet.copyOf(JLS_ORDER));
}
