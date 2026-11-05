class Fixture {
    @SuppressWarnings("serial") // not serializable
    private volatile Object first, second;

    void method(final String text) {
        final /* shared */ String left = text, right = text;
        System.out.println(left + right);
    }
}
