import java.io.Serializable;

final class Order implements Comparable<Order>, Serializable { // ordered by number
    private static final long serialVersionUID = 1L;

    @Override
    public int compareTo(final Order other) {
        return 0;
    }

    static final class Line extends Object { // a single line
        int quantity;
    }

    interface Priced { // anything with a price
        int price();
    }

    record Total(int amount) { // the sum of the lines
    }

    enum Status { // where the order stands
        OPEN,
        CLOSED
    }
}
