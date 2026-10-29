class Fixture {
    private final int value = 1;

    public boolean equals(Fixture other) {
        return other != null && other.value == value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Fixture f && equals(f);
    }

    @Override
    public int hashCode() {
        return value;
    }
}
