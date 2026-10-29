package io.javafmt.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

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
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected()).isFalse();
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
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected()).isFalse();
        assertThat(configurable.isModified()).isFalse();
        assertThat(JavafmtSettings.getInstance(getProject()).isEnabled()).isFalse();
    }

    @Test
    public void testFormShowsWhatIsAlreadyPersisted() {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        configurable.reset();
        assertThat(checkBox(JavafmtConfigurable.ENABLED_CHECKBOX_NAME).isSelected()).isTrue();
        assertThat(configurable.isModified()).isFalse();
    }

    @Test
    public void testDisplayNameIsSet() {
        assertThat(configurable.getDisplayName()).isEqualTo("javafmt");
    }

    @Test
    void binaryPathFieldWritesThroughToSettings() {
        final var field = (TextFieldWithBrowseButton) findByName(configurable, JavafmtConfigurable.BINARY_PATH_FIELD_NAME);
        field.setText("/opt/javafmt");
        assertThat(configurable.isModified()).isTrue();
        configurable.apply();
        assertThat(JavafmtSettings.getInstance(getProject()).getBinaryPath()).isEqualTo("/opt/javafmt");
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void openingTheSettingsPageDoesNotWaitForASlowBinary(@TempDir final Path dir) throws IOException {
        final var slow = dir.resolve("javafmt-slow");
        Files.writeString(slow, "#!/bin/sh\nsleep 3\necho 'javafmt 9.9.9'\n");
        Files.setPosixFilePermissions(slow, PosixFilePermissions.fromString("rwxr-xr-x"));
        JavafmtSettings.getInstance(getProject()).setBinaryPath(slow.toString());
        // reset() runs on the EDT when the Settings dialog opens the page, so probing the binary
        // there freezes the dialog for as long as the binary takes to answer.
        final var start = System.nanoTime();
        configurable.reset();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void theSettingsPanelSaysOptimizeImportsDisagreesWithJavafmt() {
        // Ctrl+Alt+O runs IntelliJ's own import optimizer (static imports last, java.* grouped),
        // which javafmt rewrites back on the next reformat. The plugin cannot take that action
        // over without running the binary on the EDT, so the settings page has to say so.
        assertThat(UIUtil.findComponentsOfType(form, JBLabel.class).stream().map(JBLabel::getText))
                .anySatisfy(text -> assertThat(text).contains("Optimize Imports"));
    }

    private JCheckBox checkBox(final String name) {
        return (JCheckBox) findByName(configurable, name);
    }

    private JComponent findByName(final JavafmtConfigurable ignoredConfigurable, final String name) {
        return UIUtil.findComponentsOfType(form, JComponent.class)
                .stream()
                .filter(component -> name.equals(component.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component named " + name + " in the javafmt settings form"));
    }
}
