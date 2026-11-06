package io.javafmt.builder;

import com.sun.source.tree.VariableTree;
import lombok.experimental.UtilityClass;

@UtilityClass
final class Names {

    /**
     * The declared name of {@code v}, or {@code _} when it has none. javac models the unnamed
     * variable (JEP 456) as an empty name, so rendering the name verbatim emits nothing and
     * yields source that no longer compiles.
     */
    String of(final VariableTree v) {
        return v.getName().isEmpty() ? "_" : v.getName().toString();
    }
}
