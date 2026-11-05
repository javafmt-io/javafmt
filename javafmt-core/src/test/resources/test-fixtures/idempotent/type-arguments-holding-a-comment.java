import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class Fixture {
    private static final Function<Object, /* nullable */ Void> RETURN_NULL = new Function<Object, /* nullable */ Void>() {
        @Override
        public Void apply(final Object input) {
            return null;
        }
    };

    List</* names */ String> names = new ArrayList</* names */ String>();
    int[] sizes = new int[/* count */ 4];
    Object value = (/* why */ Object) names;
}
