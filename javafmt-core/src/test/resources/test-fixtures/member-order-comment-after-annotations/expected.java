@SuppressWarnings("unchecked")
final class Orders {
    /*
     * Every helper here is static.
     */
    static int count() {
        return 0;
    }

    record Line(String item) {}

    private Orders() {}
}
