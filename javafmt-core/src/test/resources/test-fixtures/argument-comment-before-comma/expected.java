class Fixture {

    Order run() {
        call(
                1, // first
                2);
        call(
                1, /* first */
                2);
        return new Order(
                1, // first
                2);
    }

    void call(final int first, final int second) {}

    enum Size {

        SMALL(
                1, // the smallest
                2),
        LARGE(3, 4);

        Size(final int width, final int height) {}
    }

    record Order(int first, int second) {}
}
