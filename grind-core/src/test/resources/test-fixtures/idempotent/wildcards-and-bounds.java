import java.util.List;
import java.util.Map;

class Fixture {
    void unbounded(List<?> items) {}

    void upper(List<? extends Number> items) {}

    void lower(List<? super Integer> items) {}

    <T extends Comparable<T> & Cloneable> T pick(T a, T b) {
        return a;
    }

    Map<? extends String, ? super Number> mixed() {
        return null;
    }
}
