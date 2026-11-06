class Fixture {

    Runnable test(final int x) {
        return switch (x) {
            case 1 -> () -> log();
            case 2 -> () -> {
                log();
                log();
            };
            default -> () -> {};
        };
    }

    static void log() {}
}
