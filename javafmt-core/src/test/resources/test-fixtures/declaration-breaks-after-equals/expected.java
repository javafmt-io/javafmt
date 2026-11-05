import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

class Registry {
    private static final ConcurrentMap<Class<? extends Comparable<String>>, ConcurrentMap<String, List<Map<String, Integer>>>> registeredCache =
            new ConcurrentHashMap<>();
    static final String NOTICE = "this string literal is long enough that moving it below its declaration would still leave the line past the width, so it stays where it was written";
    static final String SHORTER_NOTICE =
            "this string literal fits once it moves below its declaration, so the line breaks after the equals sign instead";

    static BiFunction<String, List<Number>, IndexOutOfBoundsException> wrap(
            final BiFunction<String, List<Number>, IndexOutOfBoundsException> formatter) {
        final BiFunction<String, List<Number>, IndexOutOfBoundsException> indexOutOfBoundsExceptionFormatterWithTheCallerContext =
                Registry.wrapWithContext(formatter, "context");
        return indexOutOfBoundsExceptionFormatterWithTheCallerContext;
    }

    static BiFunction<String, List<Number>, IndexOutOfBoundsException> wrapWithContext(
            final BiFunction<String, List<Number>, IndexOutOfBoundsException> formatter,
            final String context) {
        return formatter;
    }
}
