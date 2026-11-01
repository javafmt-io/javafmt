class Fixture {
    String run(final boolean open, final int count) {
        if (open
            // only when something is pending
            && count > 0) {
            run(open, count - 1);
        }
        while (open
            && // still open
            count > 0) {
            run(open, count - 1);
        }
        for (int i = 0; open
                    // pending
                    && i < count; i++) {
            run(open, count - 1);
        }
        final var ok = open
            // pending
            && count > 0;
        final var label = "count: " // the prefix
                + count;
        final var flags = 1
                // the second flag
                | 2;
        final var total = count * 2 + /* the base */ 3;
        return ok
                // when everything is ready
                ? label
                // otherwise
                : String.valueOf(flags + total);
    }
}
