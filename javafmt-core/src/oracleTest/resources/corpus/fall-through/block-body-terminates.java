class Fixture {
    int test(int x) {
        switch (x) {
            case 1: { doA(); return 1; }
            case 2:
                return 2;
        }
        return 0;
    }
    native void doA();
}
