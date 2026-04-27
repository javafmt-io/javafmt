import java.util.List;

class Fixture {
    void test(List<String> items) {
        for (String s : items) tick(s);
    }
    native void tick(String s);
}
