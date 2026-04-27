class Fixture {
    int sum(int n) {
        var total = 0;
        for (var i = 0; i < n; i++) {
            total += i;
        }
        var doubled = total * 2;
        return doubled;
    }
}
