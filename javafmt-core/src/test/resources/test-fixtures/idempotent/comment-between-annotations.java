@SuppressWarnings("unchecked")
// Kept until the importer moves.
@Deprecated
final class Orders {
    @Deprecated
    // Still read by the old reports.
    @SuppressWarnings("unused")
    static final int LIMIT = 10;

    @Override
    // The parent's contract is looser.
    @SuppressWarnings("rawtypes")
    // Checked by the caller.
    public String toString() {
        return "";
    }

    int total() {
        @SuppressWarnings("unused")
        // Read by the debugger.
        @Deprecated
        final var total = LIMIT;
        return total;
    }
}
