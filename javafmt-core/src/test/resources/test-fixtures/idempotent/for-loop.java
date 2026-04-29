class Fixture {
    void test(final int[] items) {
        for (int i = 0; i < items.length; i++) {
            process(items[i]);
        }
    }
}
