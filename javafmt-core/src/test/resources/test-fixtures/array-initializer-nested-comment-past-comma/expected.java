class Catalog {

    Object[][] contents = {
            {
                    "Shipping",
                    new String[]{
                            "Ground", // slowest
                            "Express", // fastest
                    },
            },
    };

    static final int[][] SIZES = {
            {1, 2},
            {}, // none
            {3, 4},
    };

    static final int[][] WEIGHTS = {
            {1, 2}, // light
            {3, 4}, // heavy
    };

    void ship() {
        send(
                "Ground", // the default
                new int[]{
                        1,
                        2, // small
                });
    }

    void send(final String method, final int[] sizes) {}
}
