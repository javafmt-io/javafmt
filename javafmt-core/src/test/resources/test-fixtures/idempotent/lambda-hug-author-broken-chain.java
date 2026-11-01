class Fixture {
    void check(final Order order) {
        assertThat(order)
                .satisfies(placed -> {
                    assertThat(placed.id()).isEqualTo(1);
                });
    }
}
