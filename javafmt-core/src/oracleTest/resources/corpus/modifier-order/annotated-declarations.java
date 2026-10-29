class Fixture {
    @Deprecated
    static public void legacy() {
        System.out.println("legacy");
    }

    public @Deprecated void alsoLegacy() {
        System.out.println("also legacy");
    }
}
