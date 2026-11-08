class Fixture {

    void authenticate() {
        when(jwtDecoder.decode(VALID_TOKEN))
                .thenReturn(Jwt.withTokenValue(VALID_TOKEN)
                        .header("alg", "none")
                        .claim("sub", "test-user")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build());
    }

    void publish(final Order order) {
        publisher.publishEvent(OrderPlaced.builder()
                .orderId(order.id())
                .customerId(order.customerId())
                .total(order.total())
                .placedAt(clock.instant())
                .currency(order.currency())
                .build());
        publisher.publishEvent(OrderPlaced.builder()
                .orderId(order.id())
                .customerId(order.customerId())
                .total(order.total())
                .placedAt(clock.instant())
                .build());
        repository.saveAll(tenant, orders.stream()
                .filter(order -> order.total() > 100)
                .map(order -> order.withStatus(Status.SHIPPED))
                .distinct()
                .sorted(BY_TOTAL)
                .toList());
        assertThat(
                orders.stream()
                        .filter(order -> order.total() > 100)
                        .map(order -> order.withStatus(Status.SHIPPED))
                        .distinct()
                        .sorted(BY_TOTAL)
                        .toList())
                .containsExactly(first);
        publisher.publishEvent(
                someExtremelyLongFactoryNameThatTakesUpMostOfTheLine.withAnEquallyLongMethodNameForTheFirstCall(order.id(), order.customerId())
                        .build());
    }

    OrderSummary summarize(final Order order) {
        return new OrderSummary(OrderPlaced.builder()
                .orderId(order.id())
                .customerId(order.customerId())
                .total(order.total())
                .placedAt(clock.instant())
                .currency(order.currency())
                .build());
    }

    void created(final Job createdJob) {
        log.info(
                "Successfully created. job={}/{}",
                namespace,
                createdJob.getMetadata().getName());
    }
}
