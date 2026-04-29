class Fixture {
    void test(int x) {
        switch (x) {
            case 1:
            default:
                doStuff();
                break;
            case 2:
                doOther();
                break;
        }
    }
    native void doStuff();
    native void doOther();
}
