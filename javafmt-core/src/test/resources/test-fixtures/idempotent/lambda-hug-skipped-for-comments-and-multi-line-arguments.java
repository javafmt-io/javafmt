class Fixture {

    void count(final Map<Long, Integer> visits, final long id, final Executor executor, final List<Invoice> invoices) {
        visits.compute(
                id, // the key
                (_, count) -> {
                    return count + 1;
                });
        visits.compute(
                id,
                /* the key */ (_, count) -> {
                    return count + 1;
                });
        visits.compute(
                /* the key */ id,
                (_, count) -> {
                    return count + 1;
                });
        executor.submit(
                // the task
                () -> {
                    run();
                });
        executor.invokeBoth(
                () -> {
                    first();
                },
                () -> {
                    second();
                });
        executor.invokeBoth(
                () -> {},
                () -> {
                    second();
                });
        executor.submit(
                """
                monthly invoice run
                """,
                () -> {
                    run();
                });
        executor.submit(
                invoices.stream()
                        .filter(invoice -> invoice.isOpenAndOverdueForMoreThanThirtyDaysSinceTheLastReminderWasSentToTheCustomer())
                        .map(invoice -> invoice.customerAccountNumber())
                        .toList(),
                open -> {
                    send(open);
                });
    }

    boolean anyPaid(final List<Order> orders, final boolean ready) {
        return ready
            && orders.stream()
                    .filter(order -> order.isOpen()) // open ones
                    .anyMatch(order -> {
                        return order.isPaid();
                    });
    }
}
