class OrderTotals {

    long total(final List<Order> orders) {
        var sum = 0L;
        for (final Order order : orders) {
            sum += order.amount();
        }
        for (final var order : orders) {
            audit(order);
        }
        for (final var _ : orders) {
            tick();
        }
        for (final Order order : orders) {
            audit(order);
        }
        for (Order order : orders) {
            order = order.withDiscount();
            sum += order.amount();
        }
        for (@Audited final Order order : orders) {
            audit(order);
        }
        for (final Order order : orders) {
            for (final Line line : order.lines()) {
                audit(line);
            }
        }
        for (int i = 0; i < orders.size(); i++) {
            audit(orders.get(i));
        }
        return sum;
    }
}
