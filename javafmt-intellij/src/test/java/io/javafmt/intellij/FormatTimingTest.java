package io.javafmt.intellij;

import io.javafmt.JavafmtConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FormatTimingTest {

    @ParameterizedTest
    @CsvSource({
            "IN_PROCESS, true, 12, 'javafmt: formatted Foo.java in 12 ms (in-process, analyzed)'",
            "IN_PROCESS, false, 7, 'javafmt: formatted Foo.java in 7 ms (in-process, parse-only)'",
            "NATIVE, true, 40, 'javafmt: formatted Foo.java in 40 ms (native, analyzed)'",
            "NATIVE, false, 3, 'javafmt: formatted Foo.java in 3 ms (native, parse-only)'",
    })
    void namesTheFileTheDurationTheEngineAndWhetherItWasAnalyzed(final JavafmtApplicationSettings.Engine engine,
                                                                 final boolean analyzed,
                                                                 final long millis,
                                                                 final String expected) {
        final JavafmtConfig.Analysis analysis = analyzed ? new JavafmtConfig.Analysis.Attributed("") : new JavafmtConfig.Analysis.ParseOnly();
        assertThat(JavafmtFormattingService.timing("Foo.java", engine, analysis, Duration.ofMillis(millis)))
                .isEqualTo(expected);
    }
}
