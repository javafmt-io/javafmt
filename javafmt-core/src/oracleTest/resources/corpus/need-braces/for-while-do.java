class Fixture {
    void test(int x) {
        for (int i = 0; i < 10; i++) tick();
        while (x > 0) tick();
        do tick(); while (x > 0);
    }
    native void tick();
}
