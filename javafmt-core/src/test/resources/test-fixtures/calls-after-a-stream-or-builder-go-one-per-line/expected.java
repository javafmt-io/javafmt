class Fixture {

    List<String> names(final List<User> users) {
        return users.stream()
                .map(User::name)
                .toList();
    }

    Optional<User> first(final List<User> users) {
        return users.stream()
                .findFirst();
    }

    int total(final int[] values) {
        return Arrays.stream(values)
                .sum();
    }

    List<String> headRunsThroughTheStream(final Team team) {
        return team.members().stream()
                .map(User::name)
                .sorted()
                .toList();
    }

    Order builder(final String name) {
        return Order.builder()
                .name(name)
                .build();
    }

    Audit toBuilder(final Audit audit, final List<Sku> skus) {
        return audit.toBuilder()
                .skus(skus)
                .build();
    }

    OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    User notAStream(final long id) {
        return repository.findById(id).orElseThrow();
    }

    Stream<User> streamAlone(final List<User> users) {
        return users.stream();
    }

    List<String> inATernary(final boolean active, final List<User> users) {
        return active
                ? users.stream()
                        .map(User::name)
                        .toList()
                : List.of();
    }

    void lastArgument(final List<Order> orders) {
        repository.saveAll(orders.stream()
                .map(Order::shipped)
                .toList());
    }

    List<List<String>> inALambda(final List<Order> orders) {
        return orders.stream()
                .map(order -> order.items().stream()
                        .map(Item::sku)
                        .toList())
                .toList();
    }
}
