package demo;

class Fixture {

    int first = 1;
    int second = 2;

    int grouped = 3;
    int alsoGrouped = 4;

    /** Why this one is different. */
    int documented = 5;

    int afterDocumented = 6;

    Fixture() {}

    void method() {
        final var local = 1;

        final var afterAParagraphBreak = 2;
        use(local, afterAParagraphBreak);
    }

    void use(final int a, final int b) {}
}
