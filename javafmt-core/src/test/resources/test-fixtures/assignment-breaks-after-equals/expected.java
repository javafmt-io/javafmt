import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Descriptors {

    private static Set<String> exportMetricsServiceMethod;

    static <K, V> ConcurrentHashMap<K, V> newExportMetricsServiceBuilder() {
        return new ConcurrentHashMap<>();
    }

    static Set<String> exportMetricsServiceMethod() {
        Set<String> exportMetricsServiceMethod;
        Descriptors.exportMetricsServiceMethod = exportMetricsServiceMethod =
                Descriptors.<String, CopyOnWriteArrayList<Integer>>newExportMetricsServiceBuilder()
                        .keySet();
        return exportMetricsServiceMethod;
    }
}
