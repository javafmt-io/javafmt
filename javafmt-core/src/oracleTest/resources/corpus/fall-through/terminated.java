class Fixture {
    int test(int x) {
        switch (x) {
            case 1:
                doA();
                break;
            case 2:
                return 2;
            case 3:
                throw new IllegalStateException();
        }
        return 0;
    }
    native void doA();
}
