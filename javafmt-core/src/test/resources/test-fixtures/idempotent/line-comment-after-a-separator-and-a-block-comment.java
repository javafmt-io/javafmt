class Fixture {

    int[] numbers = {
            1,
            2, /* x */ // z
    };

    void m(final int a, /* x */ // y
           final int b) {
        call(
                a, /* x */ // y
                b);
    }

    enum Mark {

        X,
        Y, /* c */ // d
    }
}
