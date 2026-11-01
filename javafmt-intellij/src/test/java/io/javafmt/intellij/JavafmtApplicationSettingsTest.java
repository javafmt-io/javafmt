package io.javafmt.intellij;

import com.intellij.util.xmlb.XmlSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtApplicationSettingsTest extends JavafmtIntellijTestCase {
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
    public void testEngineIsOneChoiceForTheWholeIde() {
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(JavafmtApplicationSettings.getInstance().getEngine()).isEqualTo(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(JavafmtApplicationSettings.getInstance()).isSameAs(JavafmtApplicationSettings.getInstance());
    }
}
