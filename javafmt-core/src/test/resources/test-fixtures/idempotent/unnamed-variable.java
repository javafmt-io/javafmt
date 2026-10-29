import java.util.List;
import java.util.function.Function;

class UnnamedVariables {

    void catchExample() {
        try {
            risky();
        } catch (Exception _) {
            // discard
        }
    }

    void forEachExample() {
        for (var _ : List.of(1, 2, 3)) {
            count();
        }
    }

    Function<String, String> lambdaExample() {
        return _ -> "constant";
    }

    void localExample() {
        var _ = value();
    }

    void patternExample(final Object obj) {
        switch (obj) {
            case String _ -> handleString();
            case Integer _ -> handleInteger();
            default -> {}
        }
    }

    private void risky() {}

    private void count() {}

    private void handleString() {}

    private void handleInteger() {}

    private int value() {
        return 1;
    }
}