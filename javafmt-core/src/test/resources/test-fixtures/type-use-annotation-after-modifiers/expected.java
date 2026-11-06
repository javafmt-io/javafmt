import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Registry {

    public static @Nullable String find(final String key) {
        return null;
    }

    private static final @Nullable String fallback = "none";

    @Deprecated
    public void legacy() {}

    @Target(ElementType.TYPE_USE)
    @interface Nullable {}
}
