class Fixture {
    void test() {
        try {
            riskyOp();
        } catch (IOException | SQLException | FooException e) {
            handleException(e);
        }
    }
}
