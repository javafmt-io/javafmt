enum Fixture {

    FIRST,
    SECOND;

    String label() {
        return name();
    }

    record Detail(String label) {}
}
