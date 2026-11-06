import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class Registry {

    private final Map<String, Integer> counts = new HashMap<>();

    private final Map<Long, Map<String, Integer>> orders = new HashMap<>();

    private static final ConcurrentMap<String, ConcurrentMap<String, Integer>> cache = new ConcurrentHashMap<>(512, 0.75f, 64);
}
