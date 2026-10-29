class Fixture {

    int test(final Object o) {
        return switch (o) {
            case Point(int x, int y) -> x + y;
            default -> 0;
        };
    }

    record Point(int x, int y) {}
}
