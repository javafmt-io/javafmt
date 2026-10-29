class Fixture {

    void spacedBraces(final int x) {
        if (x > 0) {
            report();
        }
    }

    void elseOnItsOwnLine(final int x) {
        if (x > 0) {
            report();
        } else {
            other();
        }
    }

    void noSpaceAfterIf(final int x) {
        if (x > 0) {
            report();
        }
    }

    void gluedElse(final int x) {
        if (x > 0) {
            report();
        } else {
            other();
        }
    }

    void twoStatements(final int x) {
        if (x > 0) {
            report();
            other();
        }
    }

    void loopsAlwaysExpand(final int n) {
        for (int i = 0; i < n; i++) {
            report();
        }
        while (n > 0) {
            report();
        }
    }

    void report() {}

    void other() {}
}
