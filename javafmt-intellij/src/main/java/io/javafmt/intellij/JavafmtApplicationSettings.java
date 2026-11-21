package io.javafmt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * IDE-wide javafmt settings, persisted to {@code javafmt.xml} in the IDE's configuration directory.
 *
 * <p>Which engine formats is a property of the machine rather than of a project, so it is chosen
 * once for the whole IDE and never travels with a shared {@code .idea} directory.
 */
@Getter
@Service(Service.Level.APP)
@State(name = "JavafmtApplicationSettings", storages = @Storage("javafmt.xml"))
public final class JavafmtApplicationSettings implements PersistentStateComponent<JavafmtApplicationSettings> {

    private volatile Engine engine = Engine.IN_PROCESS;

    public static JavafmtApplicationSettings getInstance() {
        return ApplicationManager.getApplication().getService(JavafmtApplicationSettings.class);
    }

    public void setEngine(final Engine engine) {
        final var leftNative = this.engine == Engine.NATIVE && engine != Engine.NATIVE;
        this.engine = Objects.requireNonNull(engine, "engine");
        if (leftNative) {
            JavafmtDaemons.stopAll();
        }
    }

    @Override
    @NotNull
    public JavafmtApplicationSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull final JavafmtApplicationSettings loaded) {
        setEngine(loaded.getEngine());
    }

    public enum Engine { IN_PROCESS, NATIVE }
}
