final class Fixture {

    static int first = 1;

    static void run() {
        call();
    }

    static int second = 2;

    /** A helper, documented. */
    record Point(int x, int y) {} // and noted

    interface Marker {}

    enum Kind { A, B }

    private Fixture() {}
}
