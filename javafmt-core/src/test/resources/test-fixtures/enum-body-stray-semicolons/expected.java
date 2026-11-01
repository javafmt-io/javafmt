enum Currency {
    EURO("EUR"),
    DOLLAR("USD");

    private final String code;

    Currency(final String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}

enum Unit {
    ;

    static int count() {
        return 0;
    }
}

enum Empty { ; }
