package io.github.jschneidereit.grind.cli;

public final class Main {

    private Main() {}

    public static void main(final String[] args) {
        final int exit;
        try {
            exit = Cli.run(args, System.in, System.out, System.err);
        } catch (final RuntimeException e) {
            System.err.println("grind: internal error: " + e.getMessage() + " — please file a bug");
            System.exit(2);
            return;
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }
}
