package io.javafmt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
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

    private final Project project;

    private final JBCheckBox enabled = checkBox(ENABLED_CHECKBOX_NAME, "Use javafmt to reformat Java files in this project");

    private final List<Toggle> toggles = List.of(new Toggle(enabled, JavafmtSettings::isEnabled, JavafmtSettings::setEnabled));

    private final ComboBox<JavafmtApplicationSettings.Engine> engine = engineComboBox();

    private final TextFieldWithBrowseButton binaryPath = binaryPathField();

    private final JBLabel binaryStatus = new JBLabel();

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
                SimpleListCellRenderer.create(
                        "",
                        engine -> switch (engine) {
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

    private static TextFieldWithBrowseButton binaryPathField() {
        final var field = new TextFieldWithBrowseButton();
        field.setName(BINARY_PATH_FIELD_NAME);
        field.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor()));
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
        final var configured = binaryPath.getText().trim();
        binaryStatus.setForeground(UIUtil.getContextHelpForeground());
        binaryStatus.setText("checking…");
        final var generation = statusGeneration.incrementAndGet();
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            final var status = describeBinary(configured);
            // A later refresh may have started while this probe was running; only the newest one
            // is allowed to write the label, or a slow probe would overwrite a fresher answer.
            ApplicationManager.getApplication()
                    .invokeLater(
                            () -> {
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
        return JavafmtBinary.resolve(configuredPath)
                .map(path -> new JavafmtBinary(path).version().reportedVersion().map(v -> path + " (" + v + ")").orElse(path.toString()))
                .orElse("not found — fix the path above, or clear it to use javafmt from PATH or a downloaded one");
    }

    @Override
    public String getDisplayName() {
        return "javafmt";
    }

    @Override
    @Nullable
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
        final var form = FormBuilder.createFormBuilder();
        toggles.forEach(t -> form.addComponent(t.box()));
        form.addLabeledComponent("Formatter (all projects):", engine);
        form.addLabeledComponent("javafmt binary:", binaryPath);
        form.addComponent(binaryStatus);
        return form.addComponent(hint).addComponentFillVertically(new JPanel(), 0).getPanel();
    }

    @Override
    public boolean isModified() {
        final var settings = JavafmtSettings.getInstance(project);
        return toggles.stream().anyMatch(t -> t.box().isSelected() != t.read().test(settings))
            || engine.getItem() != JavafmtApplicationSettings.getInstance().getEngine()
            || !binaryPath.getText().trim().equals(settings.getBinaryPath());
    }

    @Override
    public void apply() {
        final var settings = JavafmtSettings.getInstance(project);
        toggles.forEach(t -> t.write().accept(settings, t.box().isSelected()));
        JavafmtApplicationSettings.getInstance().setEngine(engine.getItem());
        settings.setBinaryPath(binaryPath.getText().trim());
        refreshStatus();
    }

    @Override
    public void reset() {
        final var settings = JavafmtSettings.getInstance(project);
        toggles.forEach(t -> t.box().setSelected(t.read().test(settings)));
        engine.setItem(JavafmtApplicationSettings.getInstance().getEngine());
        binaryPath.setText(settings.getBinaryPath());
        enableBinaryPathForNativeEngine();
        refreshStatus();
    }

    private record Toggle(JBCheckBox box, Predicate<JavafmtSettings> read, BiConsumer<JavafmtSettings, Boolean> write) {}
}
