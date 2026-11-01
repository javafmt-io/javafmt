class Fixture {
    IntegrationFlow orderFlow() {
        return flow -> flow.filter(Order.class, order -> order.total() > 0).handle(Order.class, (order, headers) -> {
            audit(headers);
            return order.total();
        });
    }

    IntegrationFlow invoiceFlow() {
        return flow -> flow.transform(Invoice.class, invoice -> invoice.customer()).handle(customer -> {
            notify(customer);
        });
    }
}
