class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (final IllegalArgumentException | IllegalStateException e) {
            handleException(e);
        }
    }
}
