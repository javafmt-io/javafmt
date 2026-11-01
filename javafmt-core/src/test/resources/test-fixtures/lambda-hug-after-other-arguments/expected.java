class Fixture {
    void count(final Map<Long, Integer> visits, final long id, final Ledger ledger) {
        visits.compute(id, (_, count) -> {
            if (count == null) {
                return 1;
            }
            return count + 1;
        });
        final var merged = visits.merge(id, 1, (previous, increment) -> {
            return previous + increment;
        });
        audit(merged);
        ledger.reconcile(openingBalanceForTheCurrentAccountingPeriod, closingBalanceForTheCurrentAccountingPeriod, (openingAmount, closingAmount) -> {
            record(openingAmount, closingAmount);
        });
    }
}
