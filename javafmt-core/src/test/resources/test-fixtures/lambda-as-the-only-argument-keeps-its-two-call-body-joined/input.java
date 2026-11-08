class Transformer {

    void transform() {
        oneArgument = someLongReceiverName.transformTheValueWithAVeryLongMethodName(element -> element.value().getEpc());
        values.forEach((key, value) -> registry.registerTheMetricWithALongName(key, value).describedAs("value"));
        created = new TransformedValueWithAVeryLongClassNameForTheTest(element -> element.value().getEpc());
        names = tenants.stream().map(tenantWithALongParameterName -> tenantWithALongParameterName.configurationForTheRegion().displayName()).toList();
    }
}
