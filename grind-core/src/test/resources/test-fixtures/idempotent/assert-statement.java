class Fixture {
    void check(final int x) {
        assert x > 0;
        assert x < 100 : "x out of range";
    }
}
