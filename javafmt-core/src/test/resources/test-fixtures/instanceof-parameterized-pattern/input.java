class Fixture {
    void test(Object obj) {
        if (obj instanceof Map.Entry<String, List<Foo>> e) {
            process(e);
        }
    }
}
