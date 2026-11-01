import java.util.List;

@Deprecated(since = "1", forRemoval = false)
class Chains {
    @SuppressWarnings({"unchecked", "rawtypes"})
    List<String> names(final List<String> values) {
        return values.stream()
                .filter(value -> value.length() > 2)
                .map(String::trim)
                .sorted()
                .toList();
    }
}
