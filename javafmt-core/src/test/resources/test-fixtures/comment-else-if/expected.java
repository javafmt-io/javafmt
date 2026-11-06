class Fixture {

    void test(final int x) {
        if (x > 0) {
            a();
        } else /* mid */ if (x < 0) {
            b();
        }
    }

    void a() {}

    void b() {}
}
