class Fixture {
    private static int /*unsigned byte*/ insertOrdered(char[] source, int start, int current, int p, char c1, char c2, int /*unsigned byte*/ cc) {
        return cc + start + current + p + c1 + c2 + source.length;
    }
}
