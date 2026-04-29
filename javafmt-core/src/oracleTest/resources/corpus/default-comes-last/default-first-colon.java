class Fixture {
    void test(int x) {
        switch (x) {
            default:
                doDefault();
                break;
            case 1:
                doA();
                break;
            case 2:
                doB();
                break;
        }
    }
    native void doDefault();
    native void doA();
    native void doB();
}
