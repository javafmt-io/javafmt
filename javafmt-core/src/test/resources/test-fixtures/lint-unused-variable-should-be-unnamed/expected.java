import java.util.List;
import java.util.function.BiFunction;

class Fixture {

    int count(final List<String> items, final Object o, final BiFunction<String, String, String> join) {
        var count = 0;
        for (var _ : items) {
            count++;
        }
        try {
            Integer.parseInt("1");
        } catch (NumberFormatException _) {
            count--;
        }
        if (o instanceof String _) {
            count++;
        }
        final var _ = join.apply("a", "b");
        final BiFunction<String, String, String> second = (_, b) -> b;
        return count + second.apply("x", "y").length();
    }
}
