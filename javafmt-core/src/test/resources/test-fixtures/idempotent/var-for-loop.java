class Fixture {
    void test() {
        for (var i = 0; i < 3; i++) {
            process(i);
        }
    }

    void process(final int i) {}
}
