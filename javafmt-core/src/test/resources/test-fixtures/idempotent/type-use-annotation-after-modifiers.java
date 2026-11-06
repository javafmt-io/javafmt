import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.List;

class Registry<V> {

    private final @Nullable Comparator<V> order;

    private transient @Nullable Registry<V> next;

    Registry(final @Nullable Comparator<V> order) {
        this.order = order;
    }

    public @Nullable V find(final String key) {
        return null;
    }

    @Override
    @SuppressWarnings("unused") // kept for the debugger
    public @Nullable String toString() {
        return null;
    }

    public static <T> @Nullable T first(final List<T> items) {
        return items.isEmpty() ? null : items.get(0);
    }

    static <T> @Nullable T last(final @Nullable T[] values) {
        return values[values.length - 1];
    }

    @Target(ElementType.TYPE_USE)
    @interface Nullable {}
}
