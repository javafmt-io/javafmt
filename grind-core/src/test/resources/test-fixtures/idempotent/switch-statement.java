class Fixture {
    void test(int x) {
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