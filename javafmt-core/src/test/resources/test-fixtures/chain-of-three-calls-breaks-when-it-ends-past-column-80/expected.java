class Logging {

    void consume(final Event event, final boolean isEnable) {
        log.atInfo()
                .addKeyValue("enabled", isEnable)
                .log("Received action notification event.");
    }

    Tenant tenant(final Event event) {
        return event.getHeader()
                .getTenant()
                .orElseThrow(() -> new IllegalStateException("missing tenant"));
    }

    void twoCalls(final Event event) {
        repository.findByTenantId(event.getTenantId()).ifPresent(tenant -> publisher.publish(tenant, event));
    }

    void endsAtColumn80() {
        customer.withName(name).withAge(age).withAddress(shippingAddressLineOne);
    }

    void endsAtColumn81() {
        customer.withName(name)
                .withAge(age)
                .withAddress(shippingAddressLineFour);
    }

    void openingLinePastColumn80() {
        cluster.configMapsInTheNamespace()
                .listAll()
                .forEachRemaining(configMapWithALongName -> {
                    client.resource(configMapWithALongName).delete();
                });
    }

    void openingLineByColumn80() {
        cluster.configMaps().list().forEach(configMap -> {
            client.resource(configMap).delete();
        });
    }

    List<String> tenantNames(final String yaml) {
        return yamlObjectMapper.readTree(yaml)
                .at("/radar/tenant/details")
                .properties()
                .stream()
                .map(Entry::getKey)
                .toList();
    }

    Stream<Module> modules() {
        return moduleRegistryWithAVeryLongName.findTheModulesFor(tenantIdentifier)
                .values()
                .stream();
    }
}
