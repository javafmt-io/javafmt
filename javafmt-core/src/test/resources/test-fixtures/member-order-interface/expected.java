interface Fixture {

    int value();

    class Helper {

        static int twice(final int x) {
            return x * 2;
        }
    }
}
