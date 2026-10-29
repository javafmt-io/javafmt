class Fixture extends Base {

    private final int value;

    Fixture() {
        this.value = 0;
    }

    Fixture(final int value) {
        this.value = value;
    }

    int get() {
        return value;
    }
}
