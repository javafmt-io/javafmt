import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {

    int @Marker [] field;

    int @Marker [] @Marker [] nested;

    int @Marker [] method(final int @Marker [] param) {
        return param;
    }

    @Target(ElementType.TYPE_USE)
    @interface Marker {}
}
