enum Method {
    STORED(0),
    DEFLATED(),
    UNKNOWN();

    static final int UNKNOWN_CODE = -1;

    Method() {
        this(UNKNOWN_CODE);
    }

    Method(final int code) {}
}
