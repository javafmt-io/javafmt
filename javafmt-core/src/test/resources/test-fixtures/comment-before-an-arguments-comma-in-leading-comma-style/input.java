class Reader {
    Reader(final String text, final char separator, final char quote, final boolean strict) {}

    Reader copy(final String text) {
        return new Reader(
            text.replaceAll("(^enum\\()|(\\)$)", "")
           ,','  // Separator
           ,'\'' // Quote character
           ,true // Strict quotes
        );
    }
}
