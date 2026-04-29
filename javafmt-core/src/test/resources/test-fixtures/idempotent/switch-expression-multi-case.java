class Fixture {
    String test(final int x) {
        return switch (x) {
            case 1, 2 -> "low";
            default -> "other";
        };
    }
}
