class Fixture {
    void test(final Object obj) {
        if (obj instanceof Map.Entry<String, List<Foo>> e) {
            process(e);
        }
    }
}
