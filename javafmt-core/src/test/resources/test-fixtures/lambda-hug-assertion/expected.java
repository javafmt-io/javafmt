class Fixture {

    void check(final Order order, final List<Order> orders) {
        assertThat(order).satisfies(placed -> {
            assertThat(placed.id()).isEqualTo(1);
            assertThat(placed.total()).isPositive();
        });
        assertThat(orders).isNotEmpty().allSatisfy(placed -> {
            assertThat(placed.customer()).isNotNull();
        });
    }
}
