class Fixture {
    private static final List<Map.Entry<String, List<Integer>>> ENTRIES = source.stream()
            .filter(e -> e.isActive())
            .map(e -> e.toEntry())
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.toList());
}
