class Fixture {
    String test(int x) {
        return switch (x) {
            case 1 -> "one";
            case 2 -> "two";
            default -> "other";
        };
    }
}