import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Places<T extends Comparable<List<T> /* bound */>> extends ArrayList<List<String> /* base */> {

    private Map<String, List<Integer> /* counts */> byName;
    private List<List<String> // rows
            > table;
    private Map<String, Map<String, List<Integer> /* inner */> /* outer */> layered;

    Object build(final Object raw) {
        final var map = new HashMap<String, List<Integer> /* created */>();
        final var cast = (List<List<String> /* cast */>) raw;
        final var empty = Collections.<List<Map<String, List<Integer>>> /* witness */>emptyList();
        return List.of(map, cast, empty);
    }
}
