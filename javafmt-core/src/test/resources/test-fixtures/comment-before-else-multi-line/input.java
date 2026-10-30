class Fixture {
    void test(final int x) {
        if (x > 0) {
            a();
        }

        /*
         * options
         */
        else if (x < 0) {
            b();
        }
    }

    void a() {}

    void b() {}
}
