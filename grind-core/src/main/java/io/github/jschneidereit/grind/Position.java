package io.github.jschneidereit.grind;

public sealed interface Position permits Position.At, Position.Unknown {

    record At(int line, int column, int offset) implements Position {}

    record Unknown() implements Position {
        static final Unknown INSTANCE = new Unknown();
    }

    Position UNKNOWN = Unknown.INSTANCE;
}
