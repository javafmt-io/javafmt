import java.util.List;
import java.util.Map;

class Fixture {
    void unbounded(final List<?> items) {}

    void upper(final List<? extends Number> items) {}

    void lower(final List<? super Integer> items) {}

    <T extends Comparable<T> & Cloneable> T pick(final T a, final T b) {
        return a;
    }

    Map<? extends String, ? super Number> mixed() {
        return null;
    }
}
