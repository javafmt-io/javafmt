class Fixture {

    void lock(final Object lock) {
        synchronized (lock) {
            final int _ = 1;
        }
    }
}
