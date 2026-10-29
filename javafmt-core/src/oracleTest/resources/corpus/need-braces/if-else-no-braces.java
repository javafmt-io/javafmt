class Fixture {
    void test(int x) {
        if (x > 0) doA();
        else doB();
    }
    native void doA();
    native void doB();
}
