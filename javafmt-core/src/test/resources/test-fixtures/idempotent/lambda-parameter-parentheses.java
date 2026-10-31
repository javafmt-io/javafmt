import java.util.List;
import java.util.function.Predicate;

class Fixture {
    Predicate<String> parenthesised() {
        return (t) -> t.isEmpty();
    }

    Predicate<String> bare() {
        return t -> t.isEmpty();
    }

    List<String> twoParameters(final List<String> in) {
        return in.stream().reduce(List.of(), (a, _) -> a, (a, _) -> a);
    }

    Predicate<String> typed() {
        return (final String t) -> t.isEmpty();
    }
}
