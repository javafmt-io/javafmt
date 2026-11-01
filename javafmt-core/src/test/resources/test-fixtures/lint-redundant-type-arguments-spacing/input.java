import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class Registry {
    private final Map<String,Integer> counts = new HashMap<String, Integer>();

    private final Map<Long,Map<String,Integer>> orders = new HashMap<Long, Map<String, Integer>>();

    private static final ConcurrentMap<String, ConcurrentMap<String,Integer>> cache = new ConcurrentHashMap
        <String, ConcurrentMap<String, Integer>>(512, 0.75f, 64);
}
