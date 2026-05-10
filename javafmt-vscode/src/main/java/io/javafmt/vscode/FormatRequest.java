package io.javafmt.vscode;

import org.jspecify.annotations.Nullable;

public record FormatRequest(String id, String source, @Nullable ConfigDto config) {

    public FormatRequest(final String id, final String source) {
        this(id, source, null);
    }
}
