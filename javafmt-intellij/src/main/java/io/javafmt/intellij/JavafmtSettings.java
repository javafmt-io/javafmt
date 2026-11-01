package io.javafmt.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * Per-project javafmt settings, persisted to {@code .idea/javafmt.xml}.
 *
 * <p>Off by default. Enabling javafmt takes <em>Reformat Code</em> away from IntelliJ's own
 * formatter for every Java file in the project and applies javafmt's lint rewrites on top, so it
 * has to be an explicit choice rather than a side effect of having the plugin installed.
 *
 * <p>The service is its own persisted state: {@code com.intellij.util.xmlb} saves every property
 * that has a getter and a setter, which is why this is a mutable bean rather than a record.
 */
@Getter
@Setter
@Service(Service.Level.PROJECT)
@State(name = "JavafmtSettings", storages = @Storage("javafmt.xml"))
public final class JavafmtSettings implements PersistentStateComponent<JavafmtSettings> {
    private boolean enabled;

    @NonNull
    private String binaryPath = "";

    public static JavafmtSettings getInstance(final Project project) {
        return project.getService(JavafmtSettings.class);
    }

    @Override
    @NotNull
    public JavafmtSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull final JavafmtSettings loaded) {
        XmlSerializerUtil.copyBean(loaded, this);
    }
}
