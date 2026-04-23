package io.github.jschneidereit.grind.cli;

public final class Main {

    private Main() {}

    public static void main(final String[] args) {
        final var exit = Cli.run(args, System.in, System.out, System.err);
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
