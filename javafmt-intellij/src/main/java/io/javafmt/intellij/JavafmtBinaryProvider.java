package io.javafmt.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
public final class JavafmtBinaryProvider {

    private final BinaryInstaller installer;

    private final Consumer<Task.Backgroundable> runner;

    private final Supplier<String> searchPath;

    private final VersionGate versionGate = new VersionGate(binary -> new JavafmtBinary(binary.path()).version());

    private final AtomicBoolean downloadNoticed = new AtomicBoolean();

    private final AtomicBoolean failureNoticed = new AtomicBoolean();

    private Optional<CompletableFuture<Installation>> inFlight = Optional.empty();

    public JavafmtBinaryProvider() {
        this(BinaryInstaller.forThisPlugin(), task -> ProgressManager.getInstance().run(task), JavafmtBinary::searchPath);
    }

    JavafmtBinaryProvider(final BinaryInstaller installer, final Consumer<Task.Backgroundable> runner, final Supplier<String> searchPath) {
        this.installer = Objects.requireNonNull(installer, "installer");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.searchPath = Objects.requireNonNull(searchPath, "searchPath");
    }

    public static JavafmtBinaryProvider getInstance() {
        return ApplicationManager.getApplication().getService(JavafmtBinaryProvider.class);
    }

    VersionGate versionGate() {
        return versionGate;
    }

    Optional<BinaryIdentity> onPath() {
        return JavafmtBinary.resolveOnPath(searchPath.get())
                .flatMap(BinaryIdentity::of)
                .filter(binary -> versionGate.isSupported(binary).orElse(false));
    }

    synchronized CompletableFuture<Installation> download(@Nullable final Project project) {
        final var installed = installer.installed();
        if (installed.isPresent()) {
            return CompletableFuture.completedFuture(new Installation.Installed(installed.get()));
        }
        final var running = inFlight.filter(download -> !download.isDone());
        if (running.isPresent()) {
            return running.get();
        }
        final var result = new CompletableFuture<Installation>();
        inFlight = Optional.of(result);
        runner.accept(
                new Task.Backgroundable(project, "Downloading javafmt", true) {

                    @Override
                    public void run(@NotNull final ProgressIndicator indicator) {
                        result.complete(installer.install(indicator));
                    }

                    @Override
                    public void onFinished() {
                        result.complete(new Installation.Cancelled());
                    }
                });
        return result;
    }

    boolean claimDownloadNotice() {
        return downloadNoticed.compareAndSet(false, true);
    }

    boolean claimFailureNotice() {
        return failureNoticed.compareAndSet(false, true);
    }

    String describe() {
        return onPath()
                .map(binary -> binary.path() + " (from PATH)")
                .or(() -> installer.installed().map(path -> path + " (downloaded by the plugin)"))
                .orElseGet(
                        () -> switch (installer.asset()) {
                            case ReleaseAsset.Published published -> "none yet: " + published.name() + " is downloaded on the first reformat";
                            case ReleaseAsset.Unpublished unpublished ->
                                "none: " + unpublished.reason() + ". Set a path above, or put javafmt on your PATH.";
                        });
    }
}
