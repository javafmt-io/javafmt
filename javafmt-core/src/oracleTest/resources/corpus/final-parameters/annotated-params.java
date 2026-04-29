import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

class Fixture {

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.SOURCE)
    @interface Marker {}

    String describe(@Marker String label, int count) {
        return label + ":" + count;
    }
}
