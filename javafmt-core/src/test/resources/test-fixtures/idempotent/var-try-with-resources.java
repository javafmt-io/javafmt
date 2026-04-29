class Fixture {
    void test() throws java.io.IOException {
        try (final var reader = new java.io.StringReader("hi")) {
            reader.read();
        }
    }
}
