class Fixture {
    void test(int x) {
        if (x > 0) {
            doA();
        } else if (x < 0)
            doB();
        else if (x == 0) doC();
        else {
            doD();
        }
    }
    native void doA();
    native void doB();
    native void doC();
    native void doD();
}
