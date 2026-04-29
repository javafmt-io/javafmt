class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (Exception e) {
            handleException(e);
        }
    }
}