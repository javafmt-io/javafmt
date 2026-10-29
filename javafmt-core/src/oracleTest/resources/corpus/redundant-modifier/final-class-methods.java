final class Fixture {
    private final int value = 1;

    public final int get() {
        return value;
    }

    final void report() {
        System.out.println(value);
    }
}
