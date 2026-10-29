class Fixture {

    String matched(final Object shape) {
        return switch (shape) {
            case Rectangle(
                    Point(int aVeryLongCoordinateNameOne, int aVeryLongCoordinateNameTwo),
                    Point(int aVeryLongCoordinateNameThree, int four)) ->
                "r";
            default -> "other";
        };
    }
}
