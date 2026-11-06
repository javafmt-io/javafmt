class Fixture extends Worker {

    Fixture(final ThreadGroup group) {
        this(group, () -> {
            System.out.println("started");
        });
    }

    Fixture(final ThreadGroup group, final Runnable task) {
        super(group, () -> {
            task.run();
        });
    }

    void start(final ThreadGroup group) {
        new Thread(() -> {
            System.out.println("started");
        }).start();
        final var worker = new Thread(group, () -> {
            System.out.println("working");
        });
        worker.start();
    }
}
