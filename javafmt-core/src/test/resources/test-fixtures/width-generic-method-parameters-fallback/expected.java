class LedgerService {
    <T extends Comparable<T>> ReconciliationReport reconcileQuarterlyLedgerEntriesAgainstBankStatementsForRegions(
            final LedgerRepository ledgerRepositoryForRegions,
            final BankStatementArchive bankStatementArchive) {
        return ReconciliationReport.empty();
    }
}
