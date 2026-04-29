class Fixture {
    void test(final Object o) {
        if (o instanceof String s) {
            process(s);
        }
    }

    void process(final String s) {}
}
