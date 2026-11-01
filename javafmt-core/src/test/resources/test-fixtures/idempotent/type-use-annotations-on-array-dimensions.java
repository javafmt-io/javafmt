import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {
    private final char[] @Marker [] replacements = new char[0][];

    byte @Marker [][] samples;

    double[] @Marker [] locations(final double[] @Marker [] values, final byte @Marker [][] buffers) {
        return values;
    }

    int @Marker [] @Other [] nested;

    @Target(ElementType.TYPE_USE)
    @interface Marker {}

    @Target(ElementType.TYPE_USE)
    @interface Other {}
}
