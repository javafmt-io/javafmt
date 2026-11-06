class Fixture {

    int scan(final int n) {
        var total = 0;
        for (int i = 0; i < n;) {
            total += i;
            break;
        }
        return total;
    }
}
