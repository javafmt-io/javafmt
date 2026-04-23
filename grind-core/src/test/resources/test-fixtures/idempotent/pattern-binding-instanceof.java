class Fixture {
    void test(Object o) {
        if (o instanceof String s) {
            process(s);
        }
    }

    void process(String s) {}
}
