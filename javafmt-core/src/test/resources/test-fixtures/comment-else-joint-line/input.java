class Fixture {
    void test(int x) {
        if (x > 0) {
            a();
        } else // why the second branch
        if (x < 0) {
            b();
        }
    }

    void other(int x) {
        if (x > 0) {
            a();
        } else // the fallback
        {
            b();
        }
    }

    void a() {}

    void b() {}
}
