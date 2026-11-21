import java.util.List;
import java.util.stream.Stream;

class Fixture {

    void use() {
        take(new <String>Foo());
        take(new <String, Integer>Foo());
        take(new Foo()::<String>bar);
        take(Stream.<String>of("a"));
        take(List.<String>of());
        take(List.<List<String>>of());
    }

    void take(final Object o) {}

    static class Foo {

        <T> Foo() {}

        <T> String bar(final T t) {return "";}
    }
}
