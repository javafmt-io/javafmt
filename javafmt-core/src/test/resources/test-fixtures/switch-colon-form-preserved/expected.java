class Fixture {
    int test(final int x) {
        switch (x) {
            case 1:
                doStuff();
                break;
            case 2:
                more();
                break;
            default:
                fallback();
        }
        return 0;
    }

    void doStuff() {}

    void more() {}

    void fallback() {}
}
