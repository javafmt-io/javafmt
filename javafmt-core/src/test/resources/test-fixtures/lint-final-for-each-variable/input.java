class OrderTotals {

    long total(List<Order> orders) {
        var sum = 0L;
        for (Order order : orders) {
            sum += order.amount();
        }
        for (var order : orders) {
            audit(order);
        }
        for (var _ : orders) {
            tick();
        }
        for (final Order order : orders) {
            audit(order);
        }
        for (Order order : orders) {
            order = order.withDiscount();
            sum += order.amount();
        }
        for (@Audited Order order : orders) {
            audit(order);
        }
        for (Order order : orders) {
            for (Line line : order.lines()) {
                audit(line);
            }
        }
        for (int i = 0; i < orders.size(); i++) {
            audit(orders.get(i));
        }
        return sum;
    }
}
