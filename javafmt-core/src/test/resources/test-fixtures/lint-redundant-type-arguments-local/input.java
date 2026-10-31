class Fixture {
    void test() {
        Wrapper<String> w = new SubWrapper<String>();
        w = new SubWrapper<String>();
        System.out.println(w);
    }
}
