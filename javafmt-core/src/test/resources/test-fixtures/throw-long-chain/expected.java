class Fixture {
    void test() {
        throw factory.buildException(longArgumentNameHere, anotherLongArgumentName, yetAnotherLongArgument)
                .withCause(priorException)
                .withContext(operationDescription);
    }
}
