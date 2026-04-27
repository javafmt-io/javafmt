class Fixture {
    void test(int x) {
        if (x > 0) doSomething();
        if (x < 0)
            doSomethingElse();
    }
    native void doSomething();
    native void doSomethingElse();
}
