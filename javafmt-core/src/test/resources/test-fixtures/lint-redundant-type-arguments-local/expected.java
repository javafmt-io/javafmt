class Fixture {
    void test() {
        Wrapper<String> w = new SubWrapper<>();
        w = new SubWrapper<String>();
        System.out.println(w);
    }
}
