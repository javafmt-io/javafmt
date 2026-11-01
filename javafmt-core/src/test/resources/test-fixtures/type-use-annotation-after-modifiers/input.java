import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Registry {
    static public @Nullable String find(String key) {
        return null;
    }

    final static private @Nullable String fallback = "none";

    public @Deprecated void legacy() {}

    @Target(ElementType.TYPE_USE)
    @interface Nullable {}
}
