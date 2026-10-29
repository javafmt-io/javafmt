class Fixture {
    void test(int x) {
        switch (x) {
            case 1 -> doA();
            case 2 -> doB();
            case 3 -> doC();
        }
    }
    native void doA();
    native void doB();
    native void doC();
}
