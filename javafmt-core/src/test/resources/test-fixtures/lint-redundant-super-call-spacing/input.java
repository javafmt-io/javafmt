class Account extends Ledger {
    Account () {
        super ();
    }

    Account(int balance) {
        super( );
        System.out.println(balance);
    }

    Account(boolean frozen) {
        super (frozen);
    }
}
