package com.example.orders;

import java.util.List;

@SuppressWarnings("unchecked")
final class Orders<T extends Comparable<T>> implements Comparable<Orders<T>> {

    private static final int LIMIT = 10;

    private final List<T> items;

    Orders(final List<T> items) {
        this.items = items;
    }

    @Override
    public int compareTo(final Orders<T> other) {
        return items.size() - other.items.size();
    }

    static <U> U first(final List<U> values) throws IllegalStateException {
        return values.get(0);
    }
}
