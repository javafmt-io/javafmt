class Fixture {
    void test(int x) {
        switch (x) {
            case 1 -> doA();
            default -> doDefault();
            case 2 -> doB();
        }
    }
    native void doA();
    native void doB();
    native void doDefault();
}
