class Fixture {

    int simple(final int x) {
        if (x > 0) {return 1;}
        return 0;
    }

    int withElse(final int x) {
        if (x > 0) {return 1;} else {return -1;}
    }

    int chained(final int x) {
        if (x > 0) {return 1;} else if (x < 0) {return -1;} else {return 0;}
    }

    Runnable inLambda(final int x) {
        return () -> {
            if (x > 0) {report();}
        };
    }

    String inSwitchArm(final int x) {
        return switch (x) {
            case 1 -> {
                if (x > 0) {yield "positive";}
                yield "one";
            }
            default -> "other";
        };
    }

    void report() {}
}
