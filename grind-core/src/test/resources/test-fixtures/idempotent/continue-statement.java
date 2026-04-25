class Fixture {
    void unlabeled(int n) {
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                continue;
            }
        }
    }

    void labeled(int n, int m) {
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
