class OrderFactory {
    Order create(final String customer, final int quantity) {
        return new Order(
                customer, // the billing customer, not the recipient
                quantity, "pending");
    }
}
