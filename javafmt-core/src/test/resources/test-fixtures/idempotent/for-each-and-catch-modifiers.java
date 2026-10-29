import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

class Fixture {

    void loops(final List<String> xs) {
        for (final String x : xs) {
            use(x);
        }
        for (@LocalMarker String x : xs) {
            use(x);
        }
    }

    void catches() {
        try {
            use("");
        } catch (final RuntimeException e) {
            use(e.toString());
        }
        try {
            use("");
        } catch (@ParamMarker IllegalStateException | IllegalArgumentException e) {
            use(e.toString());
        }
    }

    void use(final String s) {}

    @Target(ElementType.LOCAL_VARIABLE)
    @interface LocalMarker {}

    @Target(ElementType.PARAMETER)
    @interface ParamMarker {}
}
