class Fixture {

    static <T> Fixture make(final T seed) {
        return Fixture.<T>build(seed);
    }

    static <T> Fixture build(final T seed) {
        return new Fixture();
    }

    Inner attach(final Fixture outer) {
        return outer.new Inner(1);
    }

    class Inner {

        Inner(final int n) {}
    }
}
