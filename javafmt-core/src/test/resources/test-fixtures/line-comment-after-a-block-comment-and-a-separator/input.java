class Fixture {

    int[] numbers = {1, 2 /* x */ // z
    };

    void m(final int a /* x */, // y
           final int b) {
        call(a /* x */, // y
             b);
    }

    enum Size {
        SMALL,
        LARGE /* c */ // d
    }

    enum Note { FIRST, LAST /* spans
        two lines */ }
}
