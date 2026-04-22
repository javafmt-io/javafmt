class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (IllegalArgumentException | IllegalStateException e) {
            handleException(e);
        }
    }
}
