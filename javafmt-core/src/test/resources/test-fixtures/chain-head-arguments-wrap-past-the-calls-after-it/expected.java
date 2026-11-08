class Entries {

    Map<String, String> toData() {
        return Stream.of(
                        new SimpleEntry<>("eventId", event.getEventId()),
                        new SimpleEntry<>("metadata", event.hasMetadata() ? event.getMetadata() : null),
                        new SimpleEntry<>("schemaVersion", schemaVersion))
                .filter(entry -> !isEmpty(entry.getValue()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    Object submitted() throws Exception {
        return executor.submit(() -> {
            work();
        })
                .get();
    }
}
