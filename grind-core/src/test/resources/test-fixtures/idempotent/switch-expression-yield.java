class Fixture {
    int test(int x) {
        return switch (x) {
            case 1 -> {
                int y = x + 1;
                yield y * 2;
            }
            default -> 0;
        };
    }
}
