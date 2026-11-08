class Entries {

    Map<String, String> toData() {
        return Map.ofEntries(
                new SimpleEntry<>("eventId", event.getEventId()),
                new SimpleEntry<>("metadata", event.hasMetadata() ? event.getMetadata() : null),
                new SimpleEntry<>("schemaVersion", schemaVersion));
    }

    void published() {
        log.info(
                "Published action notification event. eventId={}, topic={}, messageId={}, tenantId={}, spaceId={}",
                event.getEventId(),
                request.topic(),
                messageId,
                event.getTenantId(),
                event.getSpaceId());
    }

    Result fitsOnTheNextLine() {
        return service.computeTheResultFor(
                customerIdentifier, orderNumber, deliveryAddress, billingAddress, paymentMethod, discountCode, shippingOption);
    }
}
