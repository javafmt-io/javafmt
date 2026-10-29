class Fixture {
    void test(int x) {
        if (x > 0) {
            a();
        } else /* mid */ if (x < 0) {
            b();
        }
    }

    void a() {}

    void b() {}
}
