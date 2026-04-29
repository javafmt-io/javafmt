class Fixture {
    void test(final int x) {
        switch (x) {
            case 1 -> doSomething();
            case 2 -> {
                doThis();
                doThat();
            }
            default -> doOther();
        }
    }
}
