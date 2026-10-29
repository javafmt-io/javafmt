class Fixture {

    int value = 1;

    void run() {
        call();
    }

    /** A helper, documented. */
    record Point(int x, int y) {} // and noted

    interface Marker {}
}
