class Fixture {
    record Point(int x, int y) {}

    int test(final Object o) {
        return switch (o) {
            case Point(int x, int y) -> x + y;
            default -> 0;
        };
    }
}
