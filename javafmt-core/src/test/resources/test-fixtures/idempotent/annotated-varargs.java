import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {
    void plain(final String... args) {}

    void annotated(final String @Nullable ... args) {}

    void annotatedArrayElement(final byte[] @Nullable ... keys) {}

    void annotatedOuterArray(final byte @Nullable []... keys) {}

    void annotatedBoth(final byte @Nullable [] @Marker ... keys) {}
}

@Target(ElementType.TYPE_USE)
@interface Nullable {}

@Target(ElementType.TYPE_USE)
@interface Marker {}
