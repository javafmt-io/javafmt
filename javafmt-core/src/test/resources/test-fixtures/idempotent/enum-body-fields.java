enum E {

    A,
    B;

    static final Object LOCK = new Object();

    private int n;

    int next() {
        return ++n;
    }
}
