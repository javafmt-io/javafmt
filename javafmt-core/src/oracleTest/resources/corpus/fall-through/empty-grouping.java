class Fixture {
    void test(int x) {
        switch (x) {
            case 1:
            case 2:
                doStuff();
                break;
            case 3:
                doOther();
                break;
        }
    }
    native void doStuff();
    native void doOther();
}
