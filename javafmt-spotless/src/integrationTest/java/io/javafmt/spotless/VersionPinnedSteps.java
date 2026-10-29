package io.javafmt.spotless;

import com.diffplug.spotless.FormatterStep;
import io.javafmt.JavafmtConfig;

/**
 * Builds a step pinned to a chosen javafmt version, for the generated TestKit build to call.
 *
 * <p>The integration test has to prove that upgrading javafmt invalidates Spotless's up-to-date
 * state — the whole reason {@code create()} exists instead of {@code custom()}. It cannot do that
 * by actually releasing a new javafmt, so it pins the version instead. Lives in this package
 * because {@link JavafmtFormatterStep#createForVersion} is package-private, and in the
 * integration-test source set because nothing shipped should let a caller claim a version the
 * formatter is not.
 */
public final class VersionPinnedSteps {

    public static FormatterStep create(final String formatterVersion) {
        return JavafmtFormatterStep.createForVersion(JavafmtConfig.defaults(), "", formatterVersion);
    }

    private VersionPinnedSteps() {}
}
