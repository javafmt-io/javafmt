class Fixture {

    int count;

    Runnable task = new Runnable() {
        @Override
        public void run() {}
    };

    class Inner {

        void run() {}
    }

    record Point(int x, int y) {}

    record Sized(int size) {

        Sized {
            assert size >= 0;
        }
    }

    interface Port {

        void send();
    }

    enum Color { RED, GREEN }

    enum Mode {

        FIRST_MODE_WITH_A_LONG_NAME_TO_FORCE_WRAPPING,
        SECOND_MODE_WITH_A_LONG_NAME_TO_FORCE_WRAPPING,
        THIRD_MODE_WITH_A_LONG_NAME,
        FOURTH_MODE,
    }
}
