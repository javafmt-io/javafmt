class Fixture {

    void verify(final int kind, final boolean strict) {
        switch (kind) {
            case 1 -> {
                if (strict) {
                    throw new IllegalStateException();
                }
                // Unknown kinds pass for now.
            }
            default -> {}
        }
    }

    int total(final int step, final Counter counter) {
        var total = 0;
        switch (step) {
            case 12, 28 -> {
                counter.add(step);
                total = counter.value(); // 12: the first pass
                // 28: the second pass
            }
            default -> {}
        }
        return total;
    }

    interface Counter {

        void add(int step);

        int value();
    }
}
