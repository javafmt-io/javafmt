class Fixture {

    enum Kind {

        ALPHA_ONE,
        BRAVO_TWO,
        CHARLIE_THREE,
        DELTA_FOUR,
        ECHO_FIVE,
        FOXTROT_SIX,
        GOLF_SEVEN,
        HOTEL_EIGHT,
        INDIA_NINE,
        JULIETT_TEN,
        KILO_ELEVEN,
        LIMA_TWELVE,
    }

    int classify(final Kind kind) {
        return switch (kind) {
            case ALPHA_ONE, BRAVO_TWO, CHARLIE_THREE, DELTA_FOUR, ECHO_FIVE, FOXTROT_SIX, GOLF_SEVEN, HOTEL_EIGHT, INDIA_NINE, JULIETT_TEN, KILO_ELEVEN -> 1;
            default -> 0;
        };
    }
}
