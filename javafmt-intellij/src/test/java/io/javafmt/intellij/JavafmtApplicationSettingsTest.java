package io.javafmt.intellij;

import com.intellij.util.xmlb.XmlSerializer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtApplicationSettingsTest extends JavafmtIntellijTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            JavafmtDaemons.getInstance(getProject()).stop();
        } finally {
            super.tearDown();
        }
    }

    @Test
    void engineDefaultsToInProcess() {
        assertThat(new JavafmtApplicationSettings().getEngine()).isEqualTo(JavafmtApplicationSettings.Engine.IN_PROCESS);
    }

    @Test
    void engineRoundTripsThroughXmlSerialization() {
        final var settings = new JavafmtApplicationSettings();
        settings.setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        final var restored = XmlSerializer.deserialize(XmlSerializer.serialize(settings), JavafmtApplicationSettings.class);
        assertThat(restored.getEngine()).isEqualTo(JavafmtApplicationSettings.Engine.NATIVE);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void aNativeFormatThatReadTheEngineBeforeItLeftNativeStartsNoDaemon(@TempDir final Path dir) throws IOException {
        final var starts = dir.resolve("starts");
        final var binary = BinaryIdentity.of(JavafmtDaemonTest.script(dir, "echo started >> '" + starts + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS))
                .orElseThrow();
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.IN_PROCESS);
        assertThat(JavafmtDaemons.getInstance(getProject()).format(binary, "class A {}", FileScope.none()))
                .isEqualTo(new Outcome.Cancelled());
        assertThat(starts).doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("waysToLeaveNative")
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void leavingTheNativeEngineStopsRunningDaemonsWhoeverChangesIt(final Consumer<JavafmtApplicationSettings> leaveNative, @TempDir final Path dir)
            throws Exception {
        final var pidFile = dir.resolve("pid");
        final var binary = BinaryIdentity.of(JavafmtDaemonTest.script(dir, "echo $$ > '" + pidFile + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS))
                .orElseThrow();
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(JavafmtDaemons.getInstance(getProject()).format(binary, "class A {}", FileScope.none()))
                .isInstanceOf(Outcome.Formatted.class);
        leaveNative.accept(JavafmtApplicationSettings.getInstance());
        JavafmtDaemonTest.awaitExit(Long.parseLong(Files.readString(pidFile).strip()));
    }

    static Stream<Arguments> waysToLeaveNative() {
        final Consumer<JavafmtApplicationSettings> setter = settings -> settings.setEngine(JavafmtApplicationSettings.Engine.IN_PROCESS);
        final Consumer<JavafmtApplicationSettings> loadedState = settings -> settings.loadState(new JavafmtApplicationSettings());
        return Stream.of(Arguments.of(Named.of("setEngine", setter)), Arguments.of(Named.of("loadState", loadedState)));
    }
}
