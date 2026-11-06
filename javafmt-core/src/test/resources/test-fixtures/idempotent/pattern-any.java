class Fixture {

    int test(final Object o) {
        return switch (o) {
            case Point(var x, _) -> x;
            case String _ -> 0;
            default -> -1;
        };
    }

    record Point(int x, int y) {}
}
