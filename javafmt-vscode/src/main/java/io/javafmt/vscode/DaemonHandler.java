package io.javafmt.vscode;

import io.javafmt.Javafmt;
import io.javafmt.JavafmtConfig;
import lombok.experimental.UtilityClass;

@UtilityClass
final class DaemonHandler {

    FormatResponse handle(final FormatRequest request) {
        final var result = Javafmt.formatWithResult(request.source(), JavafmtConfig.defaults());
        return FormatResponse.from(request.id(), result);
    }
}
