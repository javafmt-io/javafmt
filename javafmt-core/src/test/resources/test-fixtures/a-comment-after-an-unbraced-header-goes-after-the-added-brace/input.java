class Fixture {

    int run(final int count) {
        if (count > 0) // the common case
            return count;
        else // nothing counted
            return 0;
    }

    int explained(final int count) {
        if (count > 0)
            // only when positive
            return count;
        return 0;
    }

    void loop(final int[] values) {
        for (final var value : values) /* each value */
            use(value);
        for (var index = 0; index < values.length; index++) // by index
            use(index);
        while (values.length > 0) // never true here
            use(0);
        do // at least once
            use(1);
        while (values.length > 1);
    }

    void use(final int value) {}
}
