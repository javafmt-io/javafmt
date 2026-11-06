@SuppressWarnings("serial") // the ledger is never serialized
class InvoiceFormatter {

    @Override // the base description omits the invoice number
    public String toString() {
        return "invoice";
    }

    @Deprecated
    @SuppressWarnings("unused") // kept for the archived report
    void archive(final String text) {
        print(text);
    }
}
