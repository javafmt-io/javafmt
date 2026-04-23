sealed interface Shape permits Circle, Square {
    record Circle(int radius) implements Shape {}

    record Square(int side) implements Shape {}

    int dim();
}
