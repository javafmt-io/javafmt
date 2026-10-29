class Fixture {

    int test(final int input) {
        var counter = input;
        final var negated = - -input;
        final var plused = + +input;
        final var decremented = - --counter;
        final var parenthesized = -(-input);
        final var notNot = !!(input > 0);
        return negated + plused + decremented + parenthesized + (notNot ? 1 : 0);
    }
}
