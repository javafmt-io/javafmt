class Fixture {
    void run(final int count) {
        int index;
        for (index = 0 /*count / 4*/; index < count * 2; index++) {
            process(index);
        }
        for (index++ /* skip the header */; index < count; index++) {
            process(index);
        }
    }
}
