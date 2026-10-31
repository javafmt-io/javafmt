import java.util.List;
import java.util.function.BiFunction;

class Fixture {
    int count(List<String> items, Object o, BiFunction<String, String, String> join) {
        var count = 0;
        for (var item : items) {
            count++;
        }
        try {
            Integer.parseInt("1");
        } catch (NumberFormatException e) {
            count--;
        }
        if (o instanceof String s) {
            count++;
        }
        var ignored = join.apply("a", "b");
        BiFunction<String, String, String> second = (a, b) -> b;
        return count + second.apply("x", "y").length();
    }
}
