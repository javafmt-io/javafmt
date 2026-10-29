class Fixture {
    void run() {
        try {
            System.out.println("work");
        } catch (RuntimeException e) {
            // nothing useful to do here
        }
    }
}
