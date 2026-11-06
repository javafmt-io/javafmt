class Fixture {

    @Getter
    @Setter
    private int count;
    @Deprecated
    private String name;
    private @Nullable String nick;
    Object o = new Object() {
        @Deprecated
        int inner;
    };

    int m() {
        @SuppressWarnings("unused")
        final int local = count;
        @Deprecated final int marked = count + 1;
        return local + marked;
    }
}
