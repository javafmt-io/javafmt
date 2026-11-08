class Fixture {

    void reconcile(final Ledger ledger) {
        ledger.reconcile(
                openingBalanceForTheCurrentAccountingPeriod,
                closingBalanceForTheCurrentAccountingPeriod,
                (openingBalance, closingBalance) -> {
                    record(openingBalance, closingBalance);
                });
        ledger.reconcileEveryAccountForTheCurrentAccountingPeriodAndTheOneBeforeIt()
                .forEachAccountThatStillHasAnOpenBalanceAtTheEndOfThePeriod(account -> {
                    record(account);
                });
    }
}
