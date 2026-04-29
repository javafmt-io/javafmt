class Fixture {
    int sum(final int n) {
        var total = 0;
        for (var i = 0; i < n; i++) {
            total += i;
        }
        final var doubled = total * 2;
        return doubled;
    }
}
