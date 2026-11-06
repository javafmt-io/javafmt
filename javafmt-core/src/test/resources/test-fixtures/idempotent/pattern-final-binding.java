class Fixture {

    void check(final Object message) {
        if (message instanceof final String text) {
            use(text);
        }
        if (message instanceof String plain) {
            use(plain);
        }
    }

    String classify(final Object value) {
        return switch (value) {
            case final Integer number -> "int " + number;
            case final String text -> "text " + text;
            default -> "other";
        };
    }

    void use(final Object o) {
        call(o);
    }
}
