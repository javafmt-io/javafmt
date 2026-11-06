class Fixture {

    void test(final int x) {
        if (x > 0) { // about the branch
            a();
        }
        if (x < 0) { /* also about the branch */
            b();
        }
        if (x == 0) {
            // about the first statement
            a();
        }
    }

    void a() {}

    void b() {}
}
