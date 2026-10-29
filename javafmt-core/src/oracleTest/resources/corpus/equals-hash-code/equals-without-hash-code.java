class Fixture {
    private final int value = 1;

    @Override
    public boolean equals(Object other) {
        return other instanceof Fixture f && f.value == value;
    }
}
