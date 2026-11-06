sealed interface Shape permits Circle, Square {

    default int sides() {
        return 0;
    }
}

record Circle(int radius, String name) implements Shape {

    Circle {
        radius = Math.abs(radius);
    }
}

record Square(int side) implements Shape {}

record Pair<A extends Comparable<A>, B>(A first, B second) {}

enum Status {

    OPEN("open"),
    CLOSED("closed") {
        @Override
        String label() {
            return "done";
        }
    };

    private final String text;

    Status(final String text) {
        this.text = text;
    }

    String label() {
        return text;
    }
}

@interface Marker {

    String value() default "none";
}
