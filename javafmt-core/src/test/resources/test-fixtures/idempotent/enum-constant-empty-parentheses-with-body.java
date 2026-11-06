enum Direction {

    UP() {
        @Override
        Direction opposite() {
            return DOWN;
        }
    },
    DOWN() {
        @Override
        Direction opposite() {
            return UP;
        }
    };

    abstract Direction opposite();
}
