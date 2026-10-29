class Fixture extends Base {
    private final int value;

    Fixture() {
        super();
        this.value = 0;
    }

    Fixture(int value) {
        super();
        this.value = value;
    }

    int get() {
        return value;
    }
}
