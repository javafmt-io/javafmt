interface Shape {
    int area(int width, int height);

    default int perimeter(int width, int height) {
        return 2 * (width + height);
    }
}

abstract class Base {
    abstract void apply(int x);

    int twice(int x) {
        return x + x;
    }
}
