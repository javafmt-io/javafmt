final class Fixture {

    private Fixture() {}

    /** A helper, documented. */
    record Point(int x, int y) {} // and noted

    static int first = 1;

    interface Marker {}

    static void run() {
        call();
    }

    enum Kind { A, B }

    static int second = 2;
}
