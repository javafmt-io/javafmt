package io.javafmt.intellij;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtConfigurableTest extends JavafmtIntellijTestCase {

    private JavafmtConfigurable configurable;

    private JComponent form;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        configurable = new JavafmtConfigurable(getProject());
        form = configurable.createComponent();
        configurable.reset();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            configurable.disposeUIResources();
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void testFormStartsUncheckedAndUnmodified() {
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected())
                .isFalse();
        assertThat(configurable.isModified()).isFalse();
    }

    @Test
    public void testTogglingEnabledMarksTheFormModified() {
        checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).setSelected(true);
        assertThat(configurable.isModified()).isTrue();
    }

    @Test
    public void testApplyWritesThroughToSettings() {
        checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).setSelected(true);
        configurable.apply();
        final var settings = JavafmtSettings.getInstance(getProject());
        assertThat(settings.isEnabled()).isTrue();
        assertThat(configurable.isModified()).isFalse();
    }

    @Test
    public void testResetDiscardsUnappliedEdits() {
        checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).setSelected(true);
        configurable.reset();
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected())
                .isFalse();
        assertThat(configurable.isModified()).isFalse();
        assertThat(JavafmtSettings.getInstance(getProject()).isEnabled()).isFalse();
    }

    @Test
    public void testFormShowsWhatIsAlreadyPersisted() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        configurable.reset();
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected())
                .isTrue();
        assertThat(configurable.isModified()).isFalse();
    }

    @Test
    public void testDisplayNameIsSet() {
        assertThat(configurable.getDisplayName()).isEqualTo("javafmt");
    }

    @Test
    void binaryPathFieldWritesThroughToSettings() {
        final var field = (TextFieldWithBrowseButton) findByName(JavafmtConfigurable.BINARY_PATH_FIELD_NAME);
        field.setText("/opt/javafmt");
        assertThat(configurable.isModified()).isTrue();
        configurable.apply();
        assertThat(JavafmtSettings.getInstance(getProject()).getBinaryPath()).isEqualTo("/opt/javafmt");
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void openingTheSettingsPageDoesNotWaitForASlowBinary(@TempDir final Path dir) throws IOException {
        final var answered = dir.resolve("answered");
        final var slow = FakeBinaries.executable(dir, "javafmt-slow", "#!/bin/sh\nsleep 3\necho 'javafmt 9.9.9'\ntouch '" + answered + "'\n");
        JavafmtSettings.getInstance(getProject()).setBinaryPath(slow.toString());
        // reset() runs on the EDT when the Settings dialog opens the page, so probing the binary
        // there freezes the dialog for as long as the binary takes to answer.
        final var start = System.nanoTime();
        configurable.reset();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        final var status = status().getText();
        configurable.disposeUIResources();
        PlatformTestUtil.waitWithEventsDispatching("the probe never answered", () -> Files.exists(answered), 10);
        final var settled = System.nanoTime() + Duration.ofMillis(500).toNanos();
        PlatformTestUtil.waitWithEventsDispatching("the clock stopped", () -> System.nanoTime() > settled, 10);
        assertThat(status().getText()).isEqualTo(status);
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void leavingTheNativeEngineStopsTheProjectsDaemon(@TempDir final Path dir) throws Exception {
        final var pidFile = dir.resolve("pid");
        final var binary = BinaryIdentity.of(JavafmtDaemonTest.script(dir, "echo $$ > '" + pidFile + "'\n" + JavafmtDaemonTest.ECHO_REQUESTS))
                .orElseThrow();
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        configurable.reset();
        assertThat(JavafmtDaemons.getInstance(getProject()).format(binary, "class A {}", FileScope.none()))
                .isInstanceOf(Outcome.Formatted.class);
        engine().setSelectedItem(JavafmtApplicationSettings.Engine.IN_PROCESS);
        configurable.apply();
        JavafmtDaemonTest.awaitExit(Long.parseLong(Files.readString(pidFile).strip()));
    }

    @Test
    void theSettingsPanelSaysOptimizeImportsDisagreesWithJavafmt() {
        // Ctrl+Alt+O runs IntelliJ's own import optimizer (static imports last, java.* grouped),
        // which javafmt rewrites back on the next reformat. The plugin cannot take that action
        // over without running the binary on the EDT, so the settings page has to say so.
        assertThat(
                UIUtil.findComponentsOfType(form, JBLabel.class).stream()
                        .map(JBLabel::getText))
                .anySatisfy(text -> assertThat(text).contains("Optimize Imports"));
    }

    @Test
    public void testEngineStartsInProcess() {
        assertThat(engine().getSelectedItem()).isEqualTo(JavafmtApplicationSettings.Engine.IN_PROCESS);
        assertThat(findByName(JavafmtConfigurable.BINARY_PATH_FIELD_NAME).isEnabled())
                .isFalse();
    }

    @Test
    public void testChoosingTheNativeEngineIsAppliedAndEnablesTheBinaryPath() {
        engine().setSelectedItem(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(configurable.isModified()).isTrue();
        assertThat(findByName(JavafmtConfigurable.BINARY_PATH_FIELD_NAME).isEnabled())
                .isTrue();
        configurable.apply();
        assertThat(JavafmtApplicationSettings.getInstance().getEngine()).isEqualTo(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(configurable.isModified()).isFalse();
    }

    @Test
    public void testFormShowsThePersistedEngine() {
        JavafmtApplicationSettings.getInstance().setEngine(JavafmtApplicationSettings.Engine.NATIVE);
        configurable.reset();
        assertThat(engine().getSelectedItem()).isEqualTo(JavafmtApplicationSettings.Engine.NATIVE);
        assertThat(configurable.isModified()).isFalse();
    }

    private JBLabel status() {
        return (JBLabel) findByName(JavafmtConfigurable.BINARY_STATUS_LABEL_NAME);
    }

    private JComboBox<?> engine() {
        return (JComboBox<?>) findByName(JavafmtConfigurable.ENGINE_COMBO_BOX_NAME);
    }

    private JCheckBox checkBox(final String name) {
        return (JCheckBox) findByName(name);
    }

    private JComponent findByName(final String name) {
        return UIUtil.findComponentsOfType(form, JComponent.class).stream()
                .filter(component -> name.equals(component.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component named " + name + " in the javafmt settings form"));
    }
}
