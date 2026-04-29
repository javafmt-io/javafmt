class Foo {
    static int sx;

    static {
        sx = 1;
    }

    int ix;

    {
        ix = 2;
    }

    Foo() {}

    void run() {}
}
