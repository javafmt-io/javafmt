class Fixture {
    void test() {
        BiFunction<Foo,Bar,Baz> f = (  Foo  a ,Bar   b )->a.combine(b);
    }
}
