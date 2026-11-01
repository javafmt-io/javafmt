package io.javafmt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * IDE-wide javafmt settings, persisted to {@code javafmt.xml} in the IDE's configuration directory.
 *
 * <p>Which engine formats is a property of the machine rather than of a project, so it is chosen
 * once for the whole IDE and never travels with a shared {@code .idea} directory.
 */
@Getter
@Setter
@Service(Service.Level.APP)
@State(name = "JavafmtApplicationSettings", storages = @Storage("javafmt.xml"))
public final class JavafmtApplicationSettings implements PersistentStateComponent<JavafmtApplicationSettings> {
    @NonNull
    private Engine engine = Engine.IN_PROCESS;

    public static JavafmtApplicationSettings getInstance() {
        return ApplicationManager.getApplication().getService(JavafmtApplicationSettings.class);
    }

    @Override
    @NotNull
    public JavafmtApplicationSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull final JavafmtApplicationSettings loaded) {
        XmlSerializerUtil.copyBean(loaded, this);
    }

    public enum Engine { IN_PROCESS, NATIVE }
}
