import java.util.List;

class Fixture {
    Fixture() {}

    List<String> names() {
        call(/* none */);
        call( // nothing to pass
        );
        new Fixture(/* the default */).call();
        names().clear(/* every one */);
        return List.<String>of(/* empty */);
    }

    void call() {}
}
