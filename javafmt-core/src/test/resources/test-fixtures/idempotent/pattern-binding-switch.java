class Fixture {
    String test(final Object o) {
        return switch (o) {
            case String s -> s;
            case Integer i -> i.toString();
            default -> "";
        };
    }
}
