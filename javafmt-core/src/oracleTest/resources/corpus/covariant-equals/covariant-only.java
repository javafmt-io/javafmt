class Fixture {
    private final int value = 1;

    public boolean equals(Fixture other) {
        return other != null && other.value == value;
    }
}
