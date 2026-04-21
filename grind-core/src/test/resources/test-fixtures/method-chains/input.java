ExpectedException
    .when(() -> EqualsVerifier.forClass(Foo.class).suppress(Warning.NONFINAL_FIELDS)
    .withPrefabValues(List.class, Arrays.asList(1, 2, 3).stream().map(i -> i + 1).toList(),
    Arrays.asList(1, 2, 3).stream().map(i -> i + 2).toList()).verify()).assertFailure()
    .assertMessageContains("something");
