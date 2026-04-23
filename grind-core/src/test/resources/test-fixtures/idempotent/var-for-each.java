class Fixture {
    void test(java.util.List<String> items) {
        for (var item : items) {
            process(item);
        }
    }

    void process(String s) {}
}
