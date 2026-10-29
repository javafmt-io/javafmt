package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.util.xmlb.XmlSerializer;
import org.junit.jupiter.api.Test;

public class JavafmtSettingsTest extends JavafmtIntellijTestCase {

    @Test
    public void testDisabledByDefault() {
        assertThat(JavafmtSettings.getInstance(getProject()).isEnabled()).isFalse();
    }

    // testConfigKeepsOutputVerificationOn was deleted along with toConfig(): formatting now runs
    // out of process, so the guarantee it pinned (output verification always on) lives in
    // javafmt-core's JavafmtConfig() default and the fact that the CLI exposes no flag to turn it
    // off. There is nothing left in this module for a replacement assertion to check.
    @Test
    void binaryPathDefaultsToBlankMeaningSearchPath() {
        assertThat(new JavafmtSettings().getBinaryPath()).isEmpty();
    }

    @Test
    void binaryPathRoundTripsThroughPersistedState() {
        final var settings = new JavafmtSettings();
        settings.setBinaryPath("/usr/local/bin/javafmt");
        assertThat(settings.getState().binaryPath).isEqualTo("/usr/local/bin/javafmt");
    }

    @Test
    public void testStateRoundTrips() {
        final var settings = JavafmtSettings.getInstance(getProject());
        settings.setEnabled(true);
        final var persisted = settings.getState();
        assertThat(persisted.enabled).isTrue();
        final var reloaded = new JavafmtSettings();
        reloaded.loadState(persisted);
        assertThat(reloaded.isEnabled()).isTrue();
    }

    @Test
    public void testStateSurvivesXmlSerialization() {
        // JavafmtSettings.State is a mutable public-field bean precisely because this is how the
        // platform persists it; a record or private fields would silently round-trip to defaults.
        final var state = new JavafmtSettings.State();
        state.enabled = true;
        state.binaryPath = "/opt/javafmt";
        final var restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), JavafmtSettings.State.class);
        assertThat(restored.enabled).isTrue();
        assertThat(restored.binaryPath).isEqualTo("/opt/javafmt");
    }

    @Test
    public void testServiceIsASingletonPerProject() {
        assertThat(JavafmtSettings.getInstance(getProject())).isSameAs(JavafmtSettings.getInstance(getProject()));
    }
}
