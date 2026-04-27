class Fixture {
    int sumIndexed(int[] xs) {
        int total = 0;
        for (int i = 0; i < xs.length; i++) {
            total += xs[i];
        }
        return total;
    }
}
