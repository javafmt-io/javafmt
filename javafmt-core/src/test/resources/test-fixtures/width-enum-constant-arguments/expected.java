enum Fixture {

    FIRST_CONSTANT_NAME(
            firstArgumentValueName,
            secondArgumentValueName,
            thirdArgumentValueName,
            fourthArgumentValueName,
            fifthArgumentValueNamesLonger);

    Fixture(final Object a, final Object b, final Object c, final Object d, final Object e) {
        record();
    }
}
