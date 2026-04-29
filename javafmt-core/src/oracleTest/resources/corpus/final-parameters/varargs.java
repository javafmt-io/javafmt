class Fixture {
    int sum(int... values) {
        int total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    String join(String separator, String... parts) {
        return String.join(separator, parts);
    }
}
