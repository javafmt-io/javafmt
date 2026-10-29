import java.io.Serializable;

class Box<T> {

    T value;
}

interface Mapper<I, O> {

    O map(I input);
}

record Pair<A, B>(A first, B second) {}

class Bounded<T extends Comparable<T>> {

    T pick(final T a, final T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}

class Intersection<T extends Comparable<T> & Serializable> {

    T value;
}

sealed interface Shape<T> permits Circle {

    T tag();
}

record Circle<T>(T tag, double radius) implements Shape<T> {}
