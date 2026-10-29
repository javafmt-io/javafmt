import java.util.function.Function;

class Fixture {
    Function<Integer, Integer> outerLocalCapturedByLambda() {
        int factor = 3;
        Function<Integer, Integer> f = x -> x * factor;
        return f;
    }

    int lambdaBodyHasItsOwnReassignedLocal() {
        int outer = 5;
        Runnable r = () -> {
            int inner = 0;
            inner = inner + 1;
            System.out.println(inner);
        };
        r.run();
        return outer;
    }
}
