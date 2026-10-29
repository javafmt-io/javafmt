interface Fixture {
    static public int compute() {
        return 1;
    }

    default public String label() {
        return "fixture";
    }
}
