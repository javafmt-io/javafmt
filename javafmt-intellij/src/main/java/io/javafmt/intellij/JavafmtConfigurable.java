package io.javafmt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** The <em>Settings | Tools | javafmt</em> page backing {@link JavafmtSettings}. */
public final class JavafmtConfigurable implements Configurable {

    /**
     * Swing component names, so the settings form can be driven by name rather than by position
     * in the component tree.
     */
    static final String ENABLED_CHECKBOX_NAME = "javafmt.enabled";

    static final String BINARY_PATH_FIELD_NAME = "javafmt.binaryPath";

    static final String ENGINE_COMBO_BOX_NAME = "javafmt.engine";

    static final String BINARY_STATUS_LABEL_NAME = "javafmt.binaryStatus";

    private final Project project;

    private final JBCheckBox enabled = checkBox(ENABLED_CHECKBOX_NAME, "Use javafmt to reformat Java files in this project");

    private final ComboBox<JavafmtApplicationSettings.Engine> engine = engineComboBox();

    private final TextFieldWithBrowseButton binaryPath = binaryPathField();

    private final JBLabel binaryStatus = statusLabel();

    /** Identifies the newest probe, so an earlier slow one cannot overwrite its answer. */
    private final AtomicInteger statusGeneration = new AtomicInteger();

    public JavafmtConfigurable(final Project project) {
        this.project = Objects.requireNonNull(project, "project");
        engine.addItemListener(_ -> enableBinaryPathForNativeEngine());
    }

    private static ComboBox<JavafmtApplicationSettings.Engine> engineComboBox() {
        final var box = new ComboBox<>(JavafmtApplicationSettings.Engine.values());
        box.setName(ENGINE_COMBO_BOX_NAME);
        box.setRenderer(
                BuilderKt.textListCellRenderer("", engine -> switch (engine) {
                    case IN_PROCESS -> "Inside the IDE";
                    case NATIVE -> "Native javafmt binary";
                }));
        return box;
    }

    private void enableBinaryPathForNativeEngine() {
        final var nativeEngine = engine.getItem() == JavafmtApplicationSettings.Engine.NATIVE;
        binaryPath.setEnabled(nativeEngine);
        binaryStatus.setEnabled(nativeEngine);
    }

    private static JBCheckBox checkBox(final String name, final String text) {
        final var box = new JBCheckBox(text);
        box.setName(name);
        return box;
    }

    private static JBLabel statusLabel() {
        final var label = new JBLabel();
        label.setName(BINARY_STATUS_LABEL_NAME);
        label.setForeground(UIUtil.getContextHelpForeground());
        return label;
    }

    private static TextFieldWithBrowseButton binaryPathField() {
        final var field = new TextFieldWithBrowseButton();
        field.setName(BINARY_PATH_FIELD_NAME);
        field.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.singleFile()));
        return field;
    }

    /**
     * Shows where the binary resolved to, so a wrong path is visible here rather than at the next
     * reformat.
     *
     * <p>The probe runs {@code javafmt --version}, a subprocess that can take as long as the
     * binary takes to answer. {@code reset()} and {@code apply()} are called on the EDT, so doing
     * that inline froze the Settings dialog — the first screen a new user visits — for up to
     * {@code JavafmtBinary}'s ten-second bound. The label is filled in from a pooled thread
     * instead and shows "checking…" until the answer arrives.
     */
    private void refreshStatus() {
        final var configured = configuredBinaryPath();
        binaryStatus.setText("checking…");
        final var generation = statusGeneration.incrementAndGet();
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            final var status = describeBinary(configured);
            // A later refresh may have started while this probe was running; only the newest one
            // is allowed to write the label, or a slow probe would overwrite a fresher answer.
            ApplicationManager.getApplication()
                    .invokeLater(() -> {
                        if (generation == statusGeneration.get()) {
                            binaryStatus.setText(status);
                        }
                    },
                            ModalityState.any());
        });
    }

    private static String describeBinary(final String configuredPath) {
        if (configuredPath.isBlank()) {
            return JavafmtBinaryProvider.getInstance().describe();
        }
        return JavafmtBinary.executableAt(configuredPath)
                .map(path -> JavafmtBinary.version(path)
                        .reportedVersion()
                        .map(v -> path + " (" + v + ")")
                        .orElse(path.toString()))
                .orElse("not found — fix the path above, or clear it to use javafmt from PATH or a downloaded one");
    }

    @Override
    public String getDisplayName() {
        return "javafmt";
    }

    @Override
    public JComponent createComponent() {
        final var hint = new JBLabel("<html>When enabled, javafmt replaces IntelliJ's formatter for whole-file <i>Reformat Code</i> on Java files."
                                             + " Reformatting a selection, and any request that may only touch whitespace, still goes to IntelliJ's"
                                             + " own formatter. If javafmt cannot parse the file, or the native binary is selected and none is found,"
                                             + " the file is left exactly as it was and a notification says why. Each format's duration is"
                                             + " written to idea.log, so the two formatters can be compared."
                                             + "<br/><br/><i>Optimize Imports</i> (Ctrl+Alt+O) is still IntelliJ's own: it puts static imports last"
                                             + " and groups <code>java.*</code> separately, where javafmt puts static imports first in one"
                                             + " ASCII-sorted group. Each will rewrite the other's ordering, so prefer <i>Reformat Code</i>.</html>");
        hint.setForeground(UIUtil.getContextHelpForeground());
        return FormBuilder.createFormBuilder()
                .addComponent(enabled)
                .addLabeledComponent("Formatter (all projects):", engine)
                .addLabeledComponent("javafmt binary:", binaryPath)
                .addComponent(binaryStatus)
                .addComponent(hint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        final var settings = JavafmtSettings.getInstance(project);
        return enabled.isSelected() != settings.isEnabled()
            || engine.getItem() != JavafmtApplicationSettings.getInstance().getEngine()
            || !configuredBinaryPath().equals(settings.getBinaryPath());
    }

    @Override
    public void apply() {
        final var settings = JavafmtSettings.getInstance(project);
        final var wasEnabled = settings.isEnabled();
        settings.setEnabled(enabled.isSelected());
        JavafmtApplicationSettings.getInstance().setEngine(engine.getItem());
        settings.setBinaryPath(configuredBinaryPath());
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
        if (!wasEnabled && settings.isEnabled()) {
            createEditorConfigInTheBackground();
        }
        refreshStatus();
    }

    private void createEditorConfigInTheBackground() {
        AppExecutorUtil.getAppExecutorService().execute(() -> JavafmtEditorConfigActivity.createIfEnabled(project));
    }

    @Override
    public void reset() {
        final var settings = JavafmtSettings.getInstance(project);
        enabled.setSelected(settings.isEnabled());
        engine.setItem(JavafmtApplicationSettings.getInstance().getEngine());
        binaryPath.setText(settings.getBinaryPath());
        enableBinaryPathForNativeEngine();
        refreshStatus();
    }

    @Override
    public void disposeUIResources() {
        statusGeneration.incrementAndGet();
    }

    private String configuredBinaryPath() {
        return binaryPath.getText().trim();
    }
}
