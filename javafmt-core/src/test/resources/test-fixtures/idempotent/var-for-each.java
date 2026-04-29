class Fixture {
    void test(final java.util.List<String> items) {
        for (var item : items) {
            process(item);
        }
    }

    void process(final String s) {}
}
