package io.javafmt.intellij;

import com.intellij.util.xmlb.XmlSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtSettingsTest extends JavafmtIntellijTestCase {

    @Test
    public void testDisabledByDefault() {
        assertThat(JavafmtSettings.getInstance(getProject()).isEnabled()).isFalse();
    }

    @Test
    void binaryPathDefaultsToBlankMeaningSearchPath() {
        assertThat(new JavafmtSettings().getBinaryPath()).isEmpty();
    }

    @Test
    public void testStateSurvivesXmlSerialization() {
        // JavafmtSettings is a mutable bean with a getter and a setter per property precisely because
        // this is how the platform persists it; a record would silently round-trip to defaults.
        final var settings = new JavafmtSettings();
        settings.setEnabled(true);
        settings.setBinaryPath("/opt/javafmt");
        final var restored = XmlSerializer.deserialize(XmlSerializer.serialize(settings), JavafmtSettings.class);
        assertThat(restored.isEnabled()).isTrue();
        assertThat(restored.getBinaryPath()).isEqualTo("/opt/javafmt");
    }
}
