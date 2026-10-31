record PaymentReceipt(String receiptNumber,
                      String payerName,
                      String payeeName,
                      long amountCents,
                      String currencyCode,
                      long settledEpochDay,
                      String remittanceReference) {
    long amountInWholeUnits() {
        return amountCents / 100;
    }
}
