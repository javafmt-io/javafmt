class Fixture {

    int count = 1; // note on a field

    int total = 2; /* and a block trailing */

    void run(final int x) {
        final var _ = 1; // trailing on a statement
        final var _ = 2;
    }
}
