import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.LOCAL_VARIABLE, ElementType.PACKAGE, ElementType.TYPE /*, ElementType.MODULE*/})
@interface Marker {}

class Ledger {

    @SuppressWarnings("unchecked" /* checked by the caller */)
    void settle() {}

    @SuppressWarnings({"all", "MissingOverride" /* static analysis */})
    void close() {}

    int read(final InputStream input) {
        @SuppressWarnings("resource" /* closed by the caller */)
        final var stream = input;
        return stream.hashCode();
    }

    @Deprecated()
    void retire() {}
}
