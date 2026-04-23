class Fixture {
    String test(int x) {
        return switch (x) {
            case 1, 2 -> "low";
            default -> "other";
        };
    }
}