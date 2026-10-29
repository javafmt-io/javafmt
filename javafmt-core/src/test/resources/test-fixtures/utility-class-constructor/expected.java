final class Fixture {

    static final int MAX = 100;

    static void run() {
        System.out.println(MAX);
    }

    // prevent instantiation
    private Fixture() {}
}
