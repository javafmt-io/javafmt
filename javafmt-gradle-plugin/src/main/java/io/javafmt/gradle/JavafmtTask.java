package io.javafmt.gradle;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

@DisableCachingByDefault(because = "Each concrete javafmt task declares its own caching.")
public abstract class JavafmtTask extends DefaultTask {

    private static final int MAX_THREADS = 4;

    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSources();

    @Input
    public abstract Property<Boolean> getAnalyze();

    @CompileClasspath
    public abstract ConfigurableFileCollection getAnalysisClasspath();

    @Input
    public abstract Property<String> getEngineHash();

    @Internal
    public abstract Property<JavaLauncher> getLauncher();

    @Internal
    public abstract Property<String> getMaxHeapSize();

    @Internal
    public abstract ListProperty<String> getJvmArgs();

    @Internal
    public abstract Property<JavafmtWorkerSlot> getWorkerSlot();

    @Internal
    public abstract DirectoryProperty getEngineDirectory();

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @Internal
    public abstract Property<String> getFormatTaskPath();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    protected void submit(final Collection<File> files, final boolean write, final Optional<File> marker) {
        final var engine = EngineJar.extractTo(getEngineDirectory().get().getAsFile(), getEngineHash().get());
        final var executable = getLauncher().get().getExecutablePath().getAsFile();
        final var maxHeapSize = getMaxHeapSize().get();
        final var jvmArgs = getJvmArgs().get();
        final var queue = getWorkerExecutor()
                .processIsolation(spec -> {
                    spec.getClasspath().from(engine);
                    spec.forkOptions(fork -> {
                        fork.setExecutable(executable);
                        fork.setMaxHeapSize(maxHeapSize);
                        fork.jvmArgs(jvmArgs);
                    });
                });
        queue.submit(
                JavafmtAction.class,
                parameters -> {
                    parameters.getFiles().from(files);
                    parameters.getAnalyze().set(getAnalyze());
                    parameters.getThreads().set(Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors()));
                    parameters.getAnalysisClasspath().set(getAnalysisClasspath().getAsPath());
                    parameters.getWrite().set(write);
                    parameters.getProjectDirectory().set(getProjectDirectory());
                    parameters.getFormatTaskPath().set(getFormatTaskPath());
                    marker.ifPresent(file -> parameters.getMarker().set(file));
                });
    }
}
