package io.github.jschneidereit.grind;

public record Position(int line, int column, int offset) {

    public static final Position UNKNOWN = new Position(0, 0, 0);
}
