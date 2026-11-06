class Fixture {

    int classify(final int x) {
        switch (x) {
            case 1: {
                return 1;
            }
            case 2: {
                final var doubled = x * 2;
                return doubled;
            }
            default:
                return 0;
        }
    }
}
