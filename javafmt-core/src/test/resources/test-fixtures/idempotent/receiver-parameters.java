import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture<T> {
    void plain(Fixture<T> this) {}

    void annotated(@Nullable Fixture<T> this, final int count) {}

    class Inner {
        Inner(@Nullable Fixture<T> Fixture.this) {}
    }
}

@Target(ElementType.TYPE_USE)
@interface Nullable {}
