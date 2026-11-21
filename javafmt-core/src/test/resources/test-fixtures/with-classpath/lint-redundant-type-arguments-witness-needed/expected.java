import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

class Scans {

    RuleScan methods() {
        return RuleScan.<MethodTree>collecting(m -> names(m), m -> count(m));
    }

    Stream<String> labels() {
        return Stream.of("a", "b");
    }

    static String names(final List<MethodTree> methods) {
        return "";
    }

    static Integer count(final List<MethodTree> methods) {
        return methods.size();
    }

    interface MethodTree {}

    interface RuleScan {

        static <T> RuleScan collecting(final Function<List<T>, String> scanner, final Function<List<T>, Integer> finish) {
            return new RuleScan() {
            };
        }
    }
}
