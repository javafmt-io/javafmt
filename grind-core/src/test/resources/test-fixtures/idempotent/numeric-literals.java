class Fixture {
    static final int HEX = 0xFF;

    static final int OCT = 010;

    static final int BIN = 0b1010;

    static final int GROUPED = 1_000_000;

    static final long LONG_HEX = 0xFFFF_FFFFL;

    static final long LONG_DEC = 9_999_999_999L;

    static final float F = 1.5F;

    static final double D = 2.5e10;

    static final double SUFFIXED = 3.14d;

    void test() {
        final var b = true;
        final var c = '\n';
        final var s = "hello";
        final Object o = null;
    }
}
