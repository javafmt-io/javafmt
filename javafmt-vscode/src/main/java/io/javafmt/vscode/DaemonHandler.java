package io.javafmt.vscode;

import io.javafmt.Javafmt;

final class DaemonHandler {

    static FormatResponse handle(final FormatRequest request) {
        final var result = Javafmt.formatWithResult(request.source());
        return FormatResponse.from(request.id(), result);
    }

    private DaemonHandler() {}
}
