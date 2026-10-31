class Fixture {

    int count = 1; // note on a field

    int total = 2; /* and a block trailing */

    void run(final int x) {
        final int _ = 1; // trailing on a statement
        final int _ = 2;
    }
}
