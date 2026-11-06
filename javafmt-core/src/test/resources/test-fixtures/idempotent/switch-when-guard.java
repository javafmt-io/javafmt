class Fixture {

    String test(final Object o) {
        return switch (o) {
            case Integer i when i > 0 -> "positive";
            case Integer i -> "non-positive";
            case String s when s.isEmpty() -> "empty";
            default -> "other";
        };
    }
}
