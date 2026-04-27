class Fixture {
    int test(final int x) {
        return switch (x) {
            case 1 -> {
                final int y = x + 1;
                yield y * 2;
            }
            default -> 0;
        };
    }
}
