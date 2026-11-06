class Fixture {

    void submit(final OrderService orders, final InvoiceLedger invoices, final Order order) {
        orders.submit(first, invoices.stream().filter(invoice -> invoice.isOpen()).toList());
        orders.submit(first, order.isOpen() && order.isPaid());
        final var shipment = new Shipment(first, invoices.stream().toList());
        orders.ship(shipment);
    }
}
