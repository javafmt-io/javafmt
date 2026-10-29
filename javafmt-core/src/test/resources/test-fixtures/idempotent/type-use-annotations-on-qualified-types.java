import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {

    Outer.@Marker Inner field;

    Outer.@Marker Inner method(final Outer.@Marker Inner param) {
        return param;
    }

    record Holder(Outer.@Marker Inner value) {}

    @Target(ElementType.TYPE_USE)
    @interface Marker {}

    static final class Outer {

        static final class Inner {}
    }
}
