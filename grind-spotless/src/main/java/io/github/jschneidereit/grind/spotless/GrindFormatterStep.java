package io.github.jschneidereit.grind.spotless;

import io.github.jschneidereit.grind.Grind;

public final class GrindFormatterStep {

    private GrindFormatterStep() {}

    public static String apply(final String source) {
        return Grind.format(source);
    }
}
