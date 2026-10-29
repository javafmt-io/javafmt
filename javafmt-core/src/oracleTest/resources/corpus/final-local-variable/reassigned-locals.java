class Fixture {
    int sum(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += i;
        }
        return total;
    }

    int counter() {
        int x = 0;
        x++;
        x = x * 2;
        return x;
    }

    int reassignInBranch(boolean flag) {
        int value = 1;
        if (flag) {
            value = 2;
        }
        return value;
    }
}
