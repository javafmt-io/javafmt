import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

class Fixture {
    Function<Object[], Object> first = (Object... args) -> args[0];
    BinaryOperator<String> left = (var a, var b) -> a + b;
    UnaryOperator<String> same = (final String text) -> text;
    BiFunction<String, String, String> named = (var, map) -> var + map;
}
