class Fixture {

    List<Outcome> update(final Api api, final List<Client> clients, final Map<String, Grant> grantsByClient) {
        return clients.stream().map(client -> update(api, client, grantsByClient.get(client.getClientId()), REQUIRED_SCOPES, client.getName(), clock.instant(), tenant)).toList();
    }

    void execute(final Service service, final Request request) {
        executor.execute(() -> service.process(request.tenantId(), request.spaceId(), request.payload(), request.receivedAt(), request.correlationId(), request.source()));
    }

    void otherArguments(final Service service, final Request request) {
        executor.schedule(Duration.ofSeconds(5), () -> service.process(request.tenantId(), request.spaceId(), request.payload(), request.receivedAt(), request.source()));
    }

    List<String> fits(final List<User> users) {
        return users.stream().map(user -> user.name().strip()).toList();
    }
}
