package io.javafmt.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

@Service(Service.Level.PROJECT)
public final class JavafmtDaemons implements Disposable {

    static final Duration FORMAT_TIMEOUT = Duration.ofSeconds(120);

    private final BooleanSupplier nativeEngine;

    private Optional<Running> current = Optional.empty();

    private boolean disposed;

    public JavafmtDaemons() {
        this(() -> JavafmtApplicationSettings.getInstance().getEngine() == JavafmtApplicationSettings.Engine.NATIVE);
    }

    JavafmtDaemons(final BooleanSupplier nativeEngine) {
        this.nativeEngine = Objects.requireNonNull(nativeEngine, "nativeEngine");
    }

    public static JavafmtDaemons getInstance(final Project project) {
        return project.getService(JavafmtDaemons.class);
    }

    Outcome format(final BinaryIdentity binary, final String source, final FileScope scope) {
        Objects.requireNonNull(binary, "binary");
        try {
            return daemonFor(binary)
                    .map(daemon -> daemon.format(source, scope))
                    .orElseGet(Outcome.Cancelled::new);
        } catch (final ExecutionException e) {
            return new Outcome.Refused("could not run " + binary.path() + ": " + e.getMessage(), false);
        }
    }

    static void stopAll() {
        Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .flatMap(project -> Optional.ofNullable(project.getServiceIfCreated(JavafmtDaemons.class)).stream())
                .forEach(JavafmtDaemons::stop);
    }

    synchronized void stop() {
        current.ifPresent(running -> running.daemon().stop());
        current = Optional.empty();
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        stop();
    }

    private synchronized Optional<JavafmtDaemon> daemonFor(final BinaryIdentity binary) throws ExecutionException {
        if (disposed || !nativeEngine.getAsBoolean()) {
            return Optional.empty();
        }
        final var reusable = current.filter(running -> running.binary().equals(binary)
            && running.daemon().isAlive());
        if (reusable.isPresent()) {
            return reusable.map(Running::daemon);
        }
        stop();
        final var started = JavafmtDaemon.start(binary.path(), FORMAT_TIMEOUT);
        current = Optional.of(new Running(binary, started));
        return Optional.of(started);
    }

    private record Running(BinaryIdentity binary, JavafmtDaemon daemon) {}
}
