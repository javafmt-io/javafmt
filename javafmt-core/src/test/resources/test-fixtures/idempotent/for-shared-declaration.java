class Fixture {

    int sum(final int n, final int[] xs) {
        var total = 0;
        for (int i = 0, len = n; i < len; i++) {
            total += xs[i];
        }
        for (int a = 0, b = 1, c = 2; a < n; a++) {
            total += b + c;
        }
        for (int i, j = 0; j < n; j++) {
            i = j;
            total += i;
        }
        for (final int i = 0, limit = n; i < limit; i += 2) {
            total += i;
        }
        return total;
    }
}
