class Tracing {

    String traceId() {
        return tracer.map(Tracer::currentSpan)
                .map(Span::context)
                .map(TraceContext::traceId)
                .filter(ObjectUtils::isNotEmpty)
                .orElse(null);
    }

    void endsAtColumn80() {
        assertThat(someService.computeTheResultFor(customerIdentifier, orderNu)).isEqualTo(expected);
    }

    void endsAtColumn81() {
        assertThat(someService.computeTheResultFor(customerIdentifier, orderNum))
                .isEqualTo(expected);
    }

    void insideAnArgument() {
        foo(assertThat(someService.computeTheResultFor(customerIdentifier, orde))
                .isEqualTo(expected));
    }

    String shortChain() {
        return user.getName().trim();
    }

    Service writtenOnOneLine() {
        return SomeServiceFactory.create()
                .withThing1(thing1)
                .withThing2(thing2)
                .withThing3(thing3)
                .build();
    }

    void assigned() {
        value = someService.computeTheResultFor(customerIdentifier, orderNumberrrrrrrrrrrrr)
                .value();
    }

    boolean endsWithLineComment(final Doc doc) {
        return switch (doc) {
            case Text(var s) ->
                s.stripTrailing().startsWith("//") || s.stripTrailing().contains("\n//");
            default -> false;
        };
    }

    Stream<Module> modules() {
        return module == null ? Stream.<Module>empty() : Optional.ofNullable(scanTheModuleWithAVeryLongName(module)).stream();
    }
}
