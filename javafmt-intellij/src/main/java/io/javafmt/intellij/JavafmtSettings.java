package io.javafmt.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Per-project javafmt settings, persisted to {@code .idea/javafmt.xml}.
 *
 * <p>Off by default. Enabling javafmt takes <em>Reformat Code</em> away from IntelliJ's own
 * formatter for every Java file in the project and applies javafmt's lint rewrites on top, so it
 * has to be an explicit choice rather than a side effect of having the plugin installed.
 */
@Service(Service.Level.PROJECT)
@State(name = "JavafmtSettings", storages = @Storage("javafmt.xml"))
public final class JavafmtSettings implements PersistentStateComponent<JavafmtSettings.State> {

    private State state = new State();

    public static JavafmtSettings getInstance(final Project project) {
        return project.getService(JavafmtSettings.class);
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull final State loaded) {
        state = loaded;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(final boolean enabled) {
        state.enabled = enabled;
    }

    public String getBinaryPath() {
        return state.binaryPath;
    }

    public void setBinaryPath(final String binaryPath) {
        state.binaryPath = Objects.requireNonNull(binaryPath, "binaryPath");
    }

    /**
     * Serialized form. {@code com.intellij.util.xmlb} persists public fields of a mutable bean,
     * which is why this is neither a record nor immutable.
     */
    public static final class State {

        public boolean enabled;

        public String binaryPath = "";
    }
}
