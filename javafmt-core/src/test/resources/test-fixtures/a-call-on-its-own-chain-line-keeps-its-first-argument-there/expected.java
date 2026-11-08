class Consumer {

    void consume() {
        log.atLevel(isValid ? Level.INFO : Level.WARN)
                .log("Received action notification event. enabled={}, valid={}, topic={}, key={}, partition={}",
                        isEnable, isValid, record.topic(), record.key(), record.partition());
    }

    void consumeMore() {
        log.atLevel(isValid ? Level.INFO : Level.WARN)
                .log("Received action notification event. enabled={}, valid={}, topic={}, key={}, partition={}, offset={}, tenantId={}",
                        isEnable,
                        isValid,
                        record.topic(),
                        record.key(),
                        record.partition(),
                        record.offset(),
                        event.getTenantId(),
                        event.getSpaceId(),
                        event.getRole(),
                        event.getEventId());
    }

    void stubs() {
        when(repository.findById(HANDLE))
                .thenReturn(Optional.of(
                        state(EdgeSagaState.Stage.OPERATIONAL, server(EdgeServerState.Status.RUNNING), before.minus(1, ChronoUnit.HOURS))));
    }

    Message message() {
        return InStoreMessagingProto.Message.newBuilder()
                .setIsmp(InStoreMessagingProto.ProtocolHeader.newBuilder()
                        .setService(source)
                        .build())
                .build();
    }

    Map<String, Set<String>> grouped() {
        return events.stream()
                .collect(Collectors.groupingBy(
                        EventRecord::tenantIdentifier, Collectors.mapping(EventRecord::eventIdentifier, Collectors.toCollection(TreeSet::new))));
    }

    Doc reduced() {
        return IntStream.iterate(spine.size() - 1, i -> i >= 0, i -> i - 1)
                .boxed()
                .reduce(at(innermost, () -> scan(leafOf(innermost), p)),
                        (inner, i) -> scanArguments(spine.get(i), typeArguments.get(i), inner, p), (_, right) -> right);
    }
}
