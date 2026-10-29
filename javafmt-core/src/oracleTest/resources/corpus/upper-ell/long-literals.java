class Fixture {
    long total = 100l;
    long mask = 0xFFl;
    long grouped = 1_000l;

    long sum() {
        return total + mask + grouped + 7l;
    }
}
