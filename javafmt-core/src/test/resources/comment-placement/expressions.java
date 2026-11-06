import java.util.List;
import java.util.Map;

class Expressions {

    Object compute(final List<String> names, final Map<String, Integer> counts, final boolean open) {
        final var size = names.size() * 2 + 1;
        final var ready = open && size > 0 || names.isEmpty();
        final var label = ready ? "ready" : "waiting";
        final var flags = 1 | 2;
        final var text = "size: " + size;
        final int[] numbers = {1, 2, 3};
        final var copy = new int[]{4, 5};
        final var count = (long) numbers.length;
        if (copy instanceof int[] array && array.length > count) {
            return label;
        }
        counts.forEach((key, value) -> System.out.println(key + value));
        final Runnable task = () -> {
            System.out.println(text);
        };
        task.run();
        final var empty = List.<String>of();
        final var upper = names.stream().map(String::toUpperCase).filter(name -> !name.isEmpty()).toList();
        final var failure = new IllegalStateException("message", null);
        return new Object() {
            @Override
            public String toString() {
                return String.valueOf(flags) + empty + upper + failure;
            }
        };
    }
}
