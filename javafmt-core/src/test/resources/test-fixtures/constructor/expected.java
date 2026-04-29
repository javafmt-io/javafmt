class Fixture {
    private final int x;

    public Fixture() {
        this(0);
    }

    public Fixture(final int x) {
        this.x = x;
    }

    <T> Fixture(final T t, final int x) {
        this(x);
        System.out.println(t);
    }

    @Inject public Fixture(final int x, final int y) {
        this.x = x + y;
    }
}
