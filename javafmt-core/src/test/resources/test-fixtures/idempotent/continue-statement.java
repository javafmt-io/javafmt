class Fixture {
    void unlabeled(final int n) {
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                continue;
            }
        }
    }

    void labeled(final int n, final int m) {
        outer:
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (i == j) {
                    continue outer;
                }
            }
        }
    }
}
