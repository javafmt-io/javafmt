class Fixture {

    Optional<Direction> authorBrokeIt(final Version requested, final Version database) {
        return Optional.of(requested.compareTo(database) >= 0
                ? Direction.ROLLFORWARD
                : Direction.ROLLBACK);
    }

    void tooLongForTheNextLine(final Request request) {
        headers.put(HEADER_NAME, request.correlationId() == null
                ? generatedCorrelationIdentifierForTheIncomingRequest(request.tenantId(), request.spaceId())
                : request.correlationId().strip());
    }

    void fitsOnTheNextLine(final Request request) {
        headers.put(
                HEADER_NAME_FOR_THE_CORRELATION_IDENT, request.correlationId() == null ? generatedCorrelationId(request) : request.correlationId());
    }

    void holdsATernaryTheAuthorBroke(final int offset, final String str, final boolean unicode) {
        writeSecurityBuffer(offset, str == null
                ? null
                : str.getBytes(unicode
                        ? StandardCharsets.UTF_16LE
                        : StandardCharsets.ISO_8859_1));
    }

    Optional<Doc> conditionIsAStream(final List<Link> links, final Doc doc) {
        return wrap(doc, links.stream()
                        .allMatch(Link::fits)
                ? Optional.of(doc)
                : Optional.empty());
    }
}
