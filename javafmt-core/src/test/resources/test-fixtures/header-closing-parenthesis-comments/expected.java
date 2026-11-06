class Fixture {

    void run(final java.io.Reader in) throws java.io.IOException {
        for (var i = 0; i < 3; i++ // one step at a time
        ) {
            in.read();
        }
        for (var i = 0; i < 3; i++
        // one step at a time
        ) {
            in.read();
        }
        try (
                var reader = new java.io.StringReader("x") // the reader
        ) {
            reader.read();
        }
        try (in /* given */) {
            in.read();
        }
    }
}
