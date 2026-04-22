class Fixture {
    void test(String value) {
        switch (value) {
            case "some-very-long-key-value-pair-that-is-designed-to-be-long" -> doSomethingWithThisRatherLongKeyValuePair("some-very-long-key-value-pair-that-is-designed-to-be-long");
            default -> doOther();
        }
    }
}