sealed interface Shape permits Circle, Square {

    int dim();

    record Circle(int radius) implements Shape {}

    record Square(int side) implements Shape {}
}
