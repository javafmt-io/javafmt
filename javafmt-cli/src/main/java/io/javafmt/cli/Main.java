package io.javafmt.cli;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class Main {

    public void main(final String[] args) {
        final var exit = Cli.run(args, System.in, System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
