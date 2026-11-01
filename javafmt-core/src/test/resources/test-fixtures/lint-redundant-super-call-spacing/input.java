class Account extends Ledger {
    Account () {
        super ();
    }

    Account(int balance) {
        super( );
        System.out.println(balance);
    }

    Account(long balance) {
        super(/* opening balance */);
    }

    Account(boolean frozen) {
        super (frozen);
    }
}
