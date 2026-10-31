class Fixture {
    void run() {
        value.first() // about first
                .second()
                .third();
        value.first() /* about first */
                .second()
                .third();
    }
}
