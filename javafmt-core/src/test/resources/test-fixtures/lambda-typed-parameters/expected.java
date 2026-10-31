class Fixture {
    void test() {
        final BiFunction<Foo, Bar, Baz> _ = (Foo a, Bar b) -> a.combine(b);
    }
}
