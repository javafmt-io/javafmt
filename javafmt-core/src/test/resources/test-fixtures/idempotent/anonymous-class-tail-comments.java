class Fixture {
    void run() {
        final Object _ = new Object() {
            int width; // last
        };
        final Object _ = new Object() {
            // only a note
        };
        final Object _ = new Object() {
            int height;
            /* before the brace */
        };
    }
}
