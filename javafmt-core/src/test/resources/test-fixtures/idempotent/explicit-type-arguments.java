import java.util.stream.Stream;

class Fixture {
    Stream<String> test() {
        return Stream.<String>of("a", "b");
    }
}
