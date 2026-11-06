public class Invoice
        extends Document
        implements Payable, // billing
            Printable, // output
            Archivable { // storage

    static final boolean DRAFT = false;
}

class Receipt
        implements Payable, // billing
            Printable {

    static final boolean COPY = true;
}

interface Statement extends Payable { // billing

    int total();
}

class Ledger {

    void close()
            throws IllegalStateException, // already closed
                UnsupportedOperationException {}
}
