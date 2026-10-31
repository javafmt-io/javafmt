class InvoiceLedger {
    InvoiceLedger(final String account, final String calendar, final int retentionDays)
            throws java.io.IOException, InterruptedException, IllegalStateException {}

    public void summarizeOutstandingInvoices(final String account, final String calendar)
            throws java.io.IOException, InterruptedException, IllegalStateException {}

    public void reconcileOutstandingInvoicesAcrossBranches(final String primaryAccount,
                                                           final String secondaryAccount,
                                                           final String settlementCalendar,
                                                           final int retentionDays)
            throws java.io.IOException, InterruptedException {}
}
