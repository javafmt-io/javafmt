class Fixture {
    void lock(final Object lock) {
        synchronized (lock) {
            final var _ = 1;
        }
    }
}
