class Fixture {

    void test(final int x) {
        if (x > 0) {
            a();
        } else /*
                * options
                */ if (x < 0) {
            b();
        }
    }

    void a() {}

    void b() {}
}
