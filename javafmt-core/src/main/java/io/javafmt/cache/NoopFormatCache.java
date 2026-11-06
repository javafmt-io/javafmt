package io.javafmt.cache;

import java.nio.file.Path;

final class NoopFormatCache implements FormatCache {

    static final NoopFormatCache INSTANCE = new NoopFormatCache();

    @Override
    public boolean isUpToDate(final Path file, final String currentContent) {
        return false;
    }

    @Override
    public void recordFormatted(final Path file, final String postFormatContent) {}

    @Override
    public void save() {}

    private NoopFormatCache() {}
}
