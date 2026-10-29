class Fixture {
    static {
        System.out.println("init");
    }

    void run() {
        try {
            System.out.println("work");
        } catch (RuntimeException e) {
            System.out.println(e);
        } finally {
            System.out.println("done");
        }
        synchronized (this) {
            System.out.println("locked");
        }
    }
}
