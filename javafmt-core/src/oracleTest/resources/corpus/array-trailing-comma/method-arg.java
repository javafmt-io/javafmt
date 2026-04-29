class Fixture {
    void use() {
        consume(new int[]{10, 20, 30});
    }

    void consume(final int[] values) {
        System.out.println(values.length);
    }
}
