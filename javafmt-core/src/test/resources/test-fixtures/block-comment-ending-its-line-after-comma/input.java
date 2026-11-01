class Header {
    static final int MAJOR = 1;
    static final int MINOR = 0;

    static final byte[] MAGIC = {
        0x4F, 0x52, /* OR */
        MAJOR, MINOR, /* version */
    };

    @SuppressWarnings({
        "unchecked", /* raw lists */
        "rawtypes"
    })
    void read() {}
}

class Reader implements Runnable, /* runs once */
        AutoCloseable {
    public void run() {}

    public void close() {}
}
