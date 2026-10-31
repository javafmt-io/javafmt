class Fixture {
    void test(final Iterable<Map.Entry<String, List<Foo>>> entries) {
        for (final Map.Entry<String, List<Foo>> e : entries) {
            process(e);
        }
    }
}
