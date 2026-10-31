class Fixture {
    int test(final int x) {
        switch (x) {
            case 1 -> doArrow();
            case 2:
                doColon();
                break;
            default -> fallback();
        }
        return 0;
    }

    void doArrow() {}

    void doColon() {}

    void fallback() {}
}
