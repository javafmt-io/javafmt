class Fixture {

    @Deprecated static final int LIMIT = 10;

    @Deprecated @Marker static final int BOTH = 15;

    @Deprecated
    static final int OWN_LINE = 20;

    @SuppressWarnings("unused")
    private static final int WITH_ARGUMENTS = 30;

    @interface Marker {}
}
