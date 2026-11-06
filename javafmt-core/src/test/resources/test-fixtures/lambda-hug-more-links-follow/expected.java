class Fixture {

    IntegrationFlow orderFlow() {
        return IntegrationFlow.from("orders")
                .handle(Order.class, (order, headers) -> {
                    audit(headers);
                    return order.total();
                })
                .channel("totals")
                .get();
    }

    void shipOpen(final List<Order> orders) {
        orders.stream()
                .filter(order -> {
                    return order.isOpen();
                })
                .forEach(order -> ship(order));
        orders.stream()
                .filter(order -> {
                    return order.isOpen();
                })
                .forEach(order -> {
                    ship(order);
                });
    }
}
