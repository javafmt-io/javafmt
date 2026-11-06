package example;

import java.util.List;

interface Source {

    @Override
    String toString();

    default Object message() {return null;}

    default String name() {return "source";}

    default int width() {return 4;}

    default String describe() {
        name();
        return name();
    }

    default String tooWideForOneLine() {
        return "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    }

    default String writtenOnSeveralLines() {
        return "kept";
    }

    default int pick(final int x) {
        return switch (x) {
            case 1 -> 2;
            default -> 3;
        };
    }
}

class Holder {

    private final List<String> names;

    Holder(final List<String> names) {this.names = names;}

    Holder() {this(List.of());}

    Object anonymous() {
        return new Object() {
            @Override
            public String toString() {return String.join(",", names);}
        };
    }
}
