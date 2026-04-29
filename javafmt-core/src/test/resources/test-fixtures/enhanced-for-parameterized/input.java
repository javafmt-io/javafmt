class Fixture {
    void test(Iterable<Map.Entry<String, List<Foo>>> entries) {
        for (Map.Entry<String, List<Foo>> e : entries) {
            process(e);
        }
    }
}
