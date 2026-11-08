class TagEventPublisher {

    private final Map<String, Function<TagEventContext<RadarTagEvent>, ?>> requiredFields = Map.of(
            "sku", t -> t.value().getSku());

    TagEventPublisher(final KafkaTemplate<String, TagEvent> kafkaTemplate, final String topicTemplate) {
        super(
                kafkaTemplate,
                new TopicNameMapper(topicTemplate)::topicByTenant,
                e -> e.value().getEpc());
    }

    Map<String, String> collect(final Map<String, JsonNode> entries) {
        return entries.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> e.getValue().asText()));
    }

    void submit() {
        executor.submitTheTaskWithALongName(
                firstArgument, secondArgument, () -> repository.findAll().size());
        threeCalls = someLongReceiverName.transform(
                firstArgument,
                secondArgument,
                element -> element.value().getEpc().getId());
    }

    void staysOnTheCallLine() {
        executor.submit(firstArgument, () -> repository.findAll().size());
        flat = someLongReceiverName.transformWithALongName(firstArgument, secondArgument, element -> element.valueAndMore());
    }
}
