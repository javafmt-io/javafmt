interface Fixture {
    public static final int MAX = 10;

    public abstract void run();

    public default int twice() {
        return MAX * 2;
    }

    public static int half() {
        return MAX / 2;
    }
}
