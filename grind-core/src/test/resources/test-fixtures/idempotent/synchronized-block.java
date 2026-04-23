class Fixture {
    void lock(Object lock) {
        synchronized (lock) {
            int x = 1;
        }
    }
}
