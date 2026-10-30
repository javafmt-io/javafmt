package io.javafmt.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class JavafmtDaemons implements Disposable {

    private Optional<Running> current = Optional.empty();

    private boolean disposed;

    public static JavafmtDaemons getInstance(final Project project) {
        return project.getService(JavafmtDaemons.class);
    }

    JavafmtBinary.Outcome format(final BinaryIdentity binary, final String source) {
        Objects.requireNonNull(binary, "binary");
        final Optional<JavafmtDaemon> daemon;
        try {
            daemon = daemonFor(binary);
        } catch (final ExecutionException e) {
            return new JavafmtBinary.Outcome.Refused("could not run " + binary.path() + ": " + e.getMessage(), false);
        }
        return daemon.map(running -> running.format(source)).orElseGet(JavafmtBinary.Outcome.Cancelled::new);
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
        if (disposed) {
            return Optional.empty();
        }
        final var reusable = current.filter(running -> running.binary().equals(binary) && running.daemon().isAlive());
        if (reusable.isPresent()) {
            return reusable.map(Running::daemon);
        }
        stop();
        final var started = JavafmtDaemon.start(binary.path(), JavafmtBinary.TIMEOUT);
        current = Optional.of(new Running(binary, started));
        return Optional.of(started);
    }

    private record Running(BinaryIdentity binary, JavafmtDaemon daemon) {}
}
