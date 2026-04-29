import java.util.List;

class Fixture {
    int sum(final int... values) {
        return values.length;
    }

    String join(final String separator, final String... parts) {
        return separator + parts.length;
    }

    static <T> List<T> listOf(final T... items) {
        return List.of(items);
    }

    static int firstOrZero(final int[]... rows) {
        return rows.length;
    }
}
