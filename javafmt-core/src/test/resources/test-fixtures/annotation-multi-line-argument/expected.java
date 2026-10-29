import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

class Fixture {

    @Values({ "a", "b", })
    void run() {}

    @Target(ElementType.METHOD)
    @interface Values {

        String[] value();
    }
}
