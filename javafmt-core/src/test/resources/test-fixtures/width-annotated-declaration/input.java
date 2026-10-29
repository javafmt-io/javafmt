import java.util.Map;
import java.util.Map.Entry;

class Fixture {

    @SuppressWarnings("unchecked") private static final Entry<String, Object>[] EMPTY = (Entry<String, Object>[]) new Entry[0];

    int count(final Map<String, Object> graph) {
        @SuppressWarnings(value = {"rawtypes", "unchecked"}) final Entry<String, Object>[] entries = (Entry<String, Object>[]) new Entry[graph.size()];
        return entries.length + EMPTY.length;
    }
}
