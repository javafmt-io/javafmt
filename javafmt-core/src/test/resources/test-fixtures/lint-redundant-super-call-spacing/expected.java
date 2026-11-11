class Account extends Ledger {

    Account() {}

    Account(final int balance) {
        System.out.println(balance);
    }

    Account(final boolean frozen) {
        super(frozen);
    }
}
