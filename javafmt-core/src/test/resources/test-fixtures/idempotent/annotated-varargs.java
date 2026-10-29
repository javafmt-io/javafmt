import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {

    void plain(final String... args) {}

    void annotated(final String @Nullable ... args) {}
}

@Target(ElementType.TYPE_USE)
@interface Nullable {}
