class Fixture {
    void check(int x) {
        assert x > 0;
        assert x < 100 : "x out of range";
    }
}
