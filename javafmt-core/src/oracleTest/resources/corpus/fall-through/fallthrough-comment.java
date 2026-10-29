class Fixture {
    void test(int x) {
        switch (x) {
            case 1:
                doA();
                // fallthrough
            case 2:
                doB();
                break;
        }
    }
    native void doA();
    native void doB();
}
