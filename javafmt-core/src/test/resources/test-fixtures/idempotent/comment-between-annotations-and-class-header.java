@SuppressWarnings("unchecked")
// Generated once, edited by hand since.
final class Orders implements Comparable<Orders> {
    @Override
    public int compareTo(final Orders other) {
        return 0;
    }

    @Deprecated
    // Kept for the old importer.
    static final class Line {
        int quantity;
    }

    @Deprecated
    /* Kept for the old exporter. */
    enum Status { OPEN, CLOSED }

    @Deprecated
    // Kept for the old reports.
    record Total(int amount) {}
}
