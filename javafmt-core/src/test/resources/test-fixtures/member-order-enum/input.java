enum Fixture {

    FIRST,
    SECOND;

    record Detail(String label) {}

    String label() {
        return name();
    }
}
