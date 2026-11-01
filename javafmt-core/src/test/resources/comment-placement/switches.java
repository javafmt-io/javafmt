class Switches {
    String describe(final Object value, final int kind) {
        switch (kind) {
            case 1:
                return "one";
            case 2, 3:
                break;
            default:
                return "many";
        }
        final var size = switch (kind) {
            case 0 -> 0;
            case 1 -> {
                yield 1;
            }
            default -> throw new IllegalStateException();
        };
        return switch (value) {
            case Integer number when number > size -> "large";
            case String text -> text;
            default -> "other";
        };
    }
}
