import java.util.List;

class Invoice {

    @Audit(
            level = 1,
            reason = "billing" // required by the ledger
    )
    void close() {}

    int count(final List<String> lines) {
        @SuppressWarnings(
                "unused" // read only by the debugger
        )
        final var copy = List.copyOf(lines);
        return copy.size();
    }

    @Audit(
            level = 2,
            reason = "refund"
            // reviewed every quarter
    )
    void refund() {}

    @Deprecated(// replaced by close
    )
    void retire() {}
}

@interface Audit {

    int level();

    String reason();
}
