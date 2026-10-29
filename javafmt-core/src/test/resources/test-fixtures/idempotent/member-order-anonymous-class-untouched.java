class Fixture {

    Runnable make() {
        return new Runnable() {

            record Inner(int x) {}

            @Override
            public void run() {
                call();
            }
        };
    }
}
