import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

class Fixture {
    Supplier<Object> ctor() {
        return Object::new;
    }

    Function<String, Integer> staticRef() {
        return Integer::parseInt;
    }

    Function<String, Integer> instanceRef() {
        return String::length;
    }

    void boundRef(final List<String> items) {
        items.forEach(System.out::println);
    }

    Supplier<int[]> arrayCtor() {
        return int[]::new;
    }
}
