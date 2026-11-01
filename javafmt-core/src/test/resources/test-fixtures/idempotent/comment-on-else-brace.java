class Fixture {
    void test(final int x) {
        if (x > 0) { // positive
            a();
        } else { // zero or negative
            b();
        }
        if (x > 1) {
            a();
        } else if (x < -1) { // far below
            b();
        } else { /* close to zero */
            a();
        }
        if (x == 0) {
            a();
        } else {
            // about the first statement
            b();
        }
    }

    void a() {}

    void b() {}
}
