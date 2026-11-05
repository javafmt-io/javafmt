class Fixture {
    @SuppressWarnings("serial") // not serializable
    private volatile Object first;
    @SuppressWarnings("serial")
    private volatile Object second;

    void method(final String text) {
        final /* shared */ String left = text;
        final String right = text;
        System.out.println(left + right);
    }
}
