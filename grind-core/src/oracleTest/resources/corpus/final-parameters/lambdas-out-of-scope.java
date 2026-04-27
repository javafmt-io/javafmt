import java.util.List;
import java.util.function.Function;

class Fixture {
    Function<Integer, Integer> doubler() {
        return x -> x * 2;
    }

    List<Integer> doubleAll(final List<Integer> xs) {
        return xs.stream().map(x -> x * 2).toList();
    }

    Runnable build() {
        return () -> System.out.println("hi");
    }
}
