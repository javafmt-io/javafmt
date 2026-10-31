class Fixture {

    void submit(final OrderService orders, final InvoiceLedger invoices, final Order order) {
        orders.submit(firstOrderIdentifier, secondOrderIdentifier, shippingAddressLine, billingAddressLine, invoiceTotalAmount, customerAccountNumber,
                invoices.stream()
                        .filter(invoice -> invoice.isOpen())
                        .toList(),
                closingBalance);
        orders.submit(firstOrderIdentifier, secondOrderIdentifier, shippingAddressLine, billingAddressLine, invoiceTotalAmount, customerAccountNumber,
                order.isOpen()
                    && order.isPaid(),
                closingBalance);
    }
}
