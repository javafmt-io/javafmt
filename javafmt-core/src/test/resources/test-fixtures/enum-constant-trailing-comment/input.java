enum E {

    A(1),
    B(2),  // did not exist
    ;

    private final int n;

    E(int n) {
        this.n = n;
    }
}
