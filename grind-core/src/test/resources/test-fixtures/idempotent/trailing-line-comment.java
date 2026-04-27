class Fixture {
    int count = 1; // note on a field

    int total = 2; /* and a block trailing */

    void run(int x) {
        final int a = 1; // trailing on a statement
        final int b = 2;
    }
}
