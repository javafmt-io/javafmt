enum Shipping {
    SHORT(1, 2),
    LONG(
            firstRateTableEntryName, secondRateTableEntryName, thirdRateTableEntryName, fourthRateTableEntryName, fifthRateTableEntryName,
            sixthRateTableEntryName),
    NOTED(
            1, // the base rate
            2),
    CUSTOM(
            firstRateTableEntryName, secondRateTableEntryName, thirdRateTableEntryName, fourthRateTableEntryName, fifthRateTableEntryName,
            sixthRateName) {
        @Override
        int surcharge() {
            return 3;
        }
    };

    Shipping(final Object... rates) {}

    int surcharge() {
        return 0;
    }
}
