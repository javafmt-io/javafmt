class Fixture {
    void submit(final OrderService orders, final InvoiceLedger invoices) {
        orders.callWithManyArguments(
                firstOrderIdentifier, secondOrderIdentifier, shippingAddressLine, billingAddressLine, invoiceTotalAmount, customerAccountNumber,
                deliveryWindowStart);
        orders.forEachPendingShipment(shipment -> {
            invoices.record(shipment);
            invoices.close(shipment);
        });
        invoices.reconcile(
                openInvoiceIdentifier,
                invoices.lookupCustomerAccount(
                        customerAccountNumber, billingAddressLine, shippingAddressLine, deliveryWindowStart, invoiceTotalAmount,
                        secondOrderIdentifier),
                closingBalance);
        final var shipment = new ShipmentRequest(
                firstOrderIdentifier, secondOrderIdentifier, shippingAddressLine, billingAddressLine, invoiceTotalAmount, customerAccountNumber);
    }

    void reject(final Order order, final long identifier) {
        throw new InvalidOrderException(
                order.name,
                "local order incompatible: " +
                        "stream order identifier = " + identifier + ", local order identifier = " + order.getOrderIdentifierValue());
    }
}
