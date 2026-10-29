import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Box<@Marker T extends Number> {

    T value;

    <@Marker U> U pick(final U a) {
        return a;
    }

    <V> V plain(final V a) {
        return a;
    }
}

@Target(ElementType.TYPE_PARAMETER)
@interface Marker {}
