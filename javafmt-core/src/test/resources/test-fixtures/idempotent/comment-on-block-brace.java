class Fixture {
    static { // class setup
        use(9);
    }

    { // instance setup
    }

    Fixture() { // nothing to set up
    }

    void empty() { // empty on purpose
    }

    void run(final int[] values) { // walks every value
        for (final var value : values) { // each value
            use(value);
        }
        for (var i = 0; i < values.length; i++) { // by index
            use(values[i]);
        }
        while (values.length > 3) { // never true here
            break;
        }
        do { // at least once
            use(0);
        } while (values.length > 4);
        try { // guarded
            use(1);
        } catch (final IllegalStateException _) { // expected
            use(2);
        } finally { // always
            use(3);
        }
        synchronized (this) { // lock
            use(4);
        }
        final Runnable task = () -> { // runs later
            use(5);
        };
        task.run();
        if (values.length > 0) { // not empty
        }
        switch (values.length) { // by size
            case 0: // nothing
                use(6);
                break;
            default:
                use(7);
        }
        { // a nested block
            use(8);
        }
    }

    static void use(final int value) {}
}
