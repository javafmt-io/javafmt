class Fixture {

    void run(final java.io.Reader in, final java.io.Reader out) throws java.io.IOException {
        for (int i = 0, j = 0; i < 3; i++, // the outer step
                j++) {
            in.read();
        }
        for (int i = 0, // the first index
                j = 0; i < j; i++) {
            in.read();
        }
        for (int i = 0, j = 0; // the second index
                i < j; i++) {
            in.read();
        }
        for (int i = 0; i < 3; // the bound
                i++) {
            in.read();
        }
        try (
                var first = new java.io.StringReader("a"); // the first reader
                var second = new java.io.StringReader("b")) {
            first.read();
            second.read();
        }
        try (
                in; // the input
                out) {
            in.read();
        }
        try (
                in; // the input
                out) {
            in.read();
        }
    }

    void copy(final int from, // the first position
              final int to) {}

    enum Size {

        SMALL, // the smallest
        LARGE,
    }
}
