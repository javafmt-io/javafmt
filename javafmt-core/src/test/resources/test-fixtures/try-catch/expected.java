class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (final Exception e) {
            handleException(e);
        }
    }
}
