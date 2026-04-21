package io.github.jschneidereit.grind.cli;

import io.github.jschneidereit.grind.Grind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class Main {

    private Main() {}

    public static void main(final String[] args) {
        try {
            final var input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            System.out.print(Grind.format(input));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
