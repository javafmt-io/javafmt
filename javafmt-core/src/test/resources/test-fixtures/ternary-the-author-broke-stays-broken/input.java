class Fixture {

    List<String> brokenBeforeBothOperators(final boolean strip, final List<String> reports) {
        final var toSave = strip
                ? stripAll(reports)
                : reports;
        return toSave;
    }

    String brokenBeforeTheQuestionMarkOnly(final String name) {
        return name.isEmpty()
                ? "anonymous" : name;
    }

    String brokenAfterTheOperators(final String name) {
        return name.isEmpty() ?
                "anonymous" :
                name;
    }

    String writtenOnOneLine(final String name) {
        return name.isEmpty() ? "anonymous" : name;
    }

    String nested(final int count) {
        return count == 0
                ? "none"
                : count == 1 ? "one" : "many";
    }
}
