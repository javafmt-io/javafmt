enum Op {

    PLUS {

        @Override
        int apply(final int a, final int b) {
            return a + b;
        }
    },
    MINUS {

        @Override
        int apply(final int a, final int b) {
            return a - b;
        }
    };

    abstract int apply(int a, int b);
}
