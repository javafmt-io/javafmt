class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (final IOException | SQLException | FooException e) {
            handleException(e);
        }
    }
}
