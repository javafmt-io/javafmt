final class Fixture {
    static private int counter;

    synchronized public void increment() {
        counter++;
    }
}
