class Fixture {
    void test() {
        final BiFunction<Foo, Bar, Baz> f = (Foo a, Bar b) -> a.combine(b);
    }
}
