import java.util.List;

class Fixture {
    void test(int x, List<String> items) {
        if (x > 0) {
            doA();
        } else if (x < 0) {
            doB();
        } else {
            doC();
        }
        while (x > 0) {
            tick();
        }
        do {
            tick();
        } while (x > 0);
        for (int i = 0; i < 10; i++) {
            tick();
        }
        for (String s : items) {
            tick();
        }
    }
    native void doA();
    native void doB();
    native void doC();
    native void tick();
}
