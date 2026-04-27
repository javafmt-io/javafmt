class Fixture {
    void lock(Object lock) {
        synchronized (lock) {
            final int x = 1;
        }
    }
}
