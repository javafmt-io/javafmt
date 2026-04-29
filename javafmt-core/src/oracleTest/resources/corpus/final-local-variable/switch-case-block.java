class Fixture {
    int classify(int x) {
        return switch (x) {
            case 1 -> {
                int doubled = x * 2;
                yield doubled;
            }
            case 2 -> {
                int squared = x * x;
                yield squared;
            }
            default -> 0;
        };
    }
}
