class Fixture {
    int size(final int kind) {
        switch (kind) {
            case 1 -> // one item
                runOne();
            case 2 -> /* two items */ runOne();
            case 3 -> { // three items
                runOne();
            }
            case 4 -> { // four items
                runOne();
            }
            default -> {}
        }
        return switch (kind) {
            case 1 -> // the smallest
                1;
            default -> 0;
        };
    }

    void runOne() {}
}
