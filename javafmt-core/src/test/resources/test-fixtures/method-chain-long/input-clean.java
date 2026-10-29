class Fixture {
    void test() {
        result.stream().filter(element -> element.isActive()).map(element -> element.getFullName()).sorted(Comparator.naturalOrder()).limit(100).collect(Collectors.toList());
    }
}
