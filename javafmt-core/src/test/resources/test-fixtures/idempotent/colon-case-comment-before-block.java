class Fixture {
    void roll(final int field) {
        switch (field) {
            case 1:
                // Rolling the month pins the value.
                {
                    use(field);
                    return;
                }
            case 2: { // the brace line
                use(field);
                return;
            }
            default:
                use(0);
        }
    }

    void use(final int value) {}
}
