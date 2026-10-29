class Fixture {

    String labelList(final String s) {
        return switch (s) {
            case "a" -> "A";
            case null, default -> "other";
        };
    }

    String nullThenDefault(final String s) {
        return switch (s) {
            case null -> "null";
            case "a" -> "A";
            default -> "other";
        };
    }
}
