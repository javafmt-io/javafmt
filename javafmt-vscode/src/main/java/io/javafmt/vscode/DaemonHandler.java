package io.javafmt.vscode;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;

final class DaemonHandler {

    static FormatResponse handle(final FormatRequest request) {
        final var config = request.config() != null
            ? new JavafmtConfig(request.config().reorderMembers())
            : JavafmtConfig.defaults();
        final var result = Javafmt.formatWithResult(request.source(), config);
        return FormatResponse.from(request.id(), result);
    }

    private DaemonHandler() {}
}
