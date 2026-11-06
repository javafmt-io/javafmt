class InvoiceFormatter {

    void archive(
            @Named("primary") // the account the archive is billed to
            final String account,
            final int copies) {
        print(account, copies);
    }

    void print(final int copies,
               @Deprecated // kept for the archived report
               final String text) {
        print(text);
    }

    record Ledger(int pages,
                  @Named("primary") // the account the ledger belongs to
                  String account) {}
}
