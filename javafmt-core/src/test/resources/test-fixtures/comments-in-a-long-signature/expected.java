class Fixture {

    private static int /*unsigned byte*/ insertOrdered(final char[] source,
                                                       final int start,
                                                       final int current,
                                                       final int p,
                                                       final char c1,
                                                       final char c2,
                                                       final int /*unsigned byte*/ cc) {
        return cc + start + current + p + c1 + c2 + source.length;
    }
}
