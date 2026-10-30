package io.javafmt.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.StreamSupport;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public abstract class JavafmtPlugin implements Plugin<Project> {

    private static final String CHECK = "javafmtCheck";

    private static final String FORMAT = "javafmtFormat";

    private static final String GROUP = "formatting";

    private static final String PROPERTIES = "javafmt-gradle-plugin.properties";

    private static final int ENGINE_JAVA_VERSION = 25;

    @Override
    public void apply(final Project project) {
        final var extension = project.getExtensions().create("javafmt", JavafmtExtension.class);
        extension.getExcludes().convention(List.of());
        extension.getAnalyze().convention(false);
        extension.getMaxHeapSize().convention("1g");
        final var workerSlot = project.getGradle()
                .getSharedServices()
                .registerIfAbsent(JavafmtWorkerSlot.NAME, JavafmtWorkerSlot.class, spec -> spec.getMaxParallelUsages().set(1));
        final var checkAll = project.getTasks()
                .register(
                        CHECK,
                        task -> describe(task, LifecycleBasePlugin.VERIFICATION_GROUP, "Checks that all Java sources are formatted with javafmt."));
        final var formatAll = project.getTasks().register(FORMAT, task -> describe(task, GROUP, "Formats all Java sources with javafmt."));
        project.getPluginManager()
                .withPlugin(
                        "java",
                        ignored -> registerSourceSetTasks(
                                new SourceSetTasks(project, extension, pluginProperties(), workerSlot, checkAll, formatAll)));
    }

    private static void registerSourceSetTasks(final SourceSetTasks sourceSetTasks) {
        final var project = sourceSetTasks.project();
        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().all(sourceSetTasks::register);
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(sourceSetTasks.checkAll()));
    }

    private static void describe(final Task task, final String group, final String description) {
        task.setGroup(group);
        task.setDescription(description);
    }

    private static PluginProperties pluginProperties() {
        try (final var in = Objects.requireNonNull(JavafmtPlugin.class.getResourceAsStream(PROPERTIES), PROPERTIES)) {
            final var properties = new Properties();
            properties.load(in);
            return new PluginProperties(
                    Objects.requireNonNull(properties.getProperty("engine"), "engine"),
                    Objects.requireNonNull(properties.getProperty("foojay"), "foojay"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record PluginProperties(String engineHash, String foojayVersion) {}

    private record SourceSetTasks(
            Project project,
            JavafmtExtension extension,
            PluginProperties properties,
            Provider<JavafmtWorkerSlot> workerSlot,
            TaskProvider<Task> checkAll,
            TaskProvider<Task> formatAll) {

        void register(final SourceSet sourceSet) {
            final var name = sourceSet.getName();
            final var suffix = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            final var format = project.getTasks()
                    .register(
                            FORMAT + suffix,
                            JavafmtFormat.class,
                            task -> {
                                describe(task, GROUP, "Formats the " + name + " Java sources with javafmt.");
                                configure(task, sourceSet);
                            });
            final var check = project.getTasks()
                    .register(
                            CHECK + suffix,
                            JavafmtCheck.class,
                            task -> {
                                describe(
                                        task,
                                        LifecycleBasePlugin.VERIFICATION_GROUP,
                                        "Checks that the " + name + " Java sources are formatted with javafmt.");
                                configure(task, sourceSet);
                                task.getMarker().set(project.getLayout().getBuildDirectory().file("javafmt/" + name + ".checked"));
                                task.mustRunAfter(format);
                            });
            checkAll.configure(task -> task.dependsOn(check));
            formatAll.configure(task -> task.dependsOn(format));
        }

        private void configure(final JavafmtTask task, final SourceSet sourceSet) {
            final var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
            task.getSources().from(project.provider(() -> javaSources(sourceSet)));
            task.getAnalyze().set(extension.getAnalyze());
            task.getAnalysisClasspath().from(extension.getAnalyze().map(analyze -> analyze ? sourceSet.getCompileClasspath() : List.of()));
            task.getEngineHash().set(properties.engineHash());
            task.getLauncher().convention(project.provider(() -> java25(toolchains, properties.foojayVersion())));
            task.getMaxHeapSize().set(extension.getMaxHeapSize());
            task.getJvmArgs().set(extension.getJvmArgs());
            task.getWorkerSlot().set(workerSlot);
            task.usesService(workerSlot);
            task.getEngineDirectory().set(new File(project.getGradle().getGradleUserHomeDir(), "caches/javafmt"));
            task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
            task.getFormatTaskPath().set(project.getPath().equals(":") ? ":" + FORMAT : project.getPath() + ":" + FORMAT);
        }

        private List<ConfigurableFileTree> javaSources(final SourceSet sourceSet) {
            final var buildDirectory = project.getLayout().getBuildDirectory().get().getAsFile().toPath();
            final var projectDirectory = project.getProjectDir().toPath();
            final var excludes = extension.getExcludes().get();
            return sourceSet.getJava()
                    .getSrcDirs()
                    .stream()
                    .map(File::toPath)
                    .filter(directory -> !directory.startsWith(buildDirectory))
                    .map(
                            directory -> project.fileTree(
                                    directory,
                                    tree -> tree.include("**/*.java")
                                            .exclude(new ProjectRelativeExcludes(segments(projectDirectory.relativize(directory)), excludes))))
                    .toList();
        }

        private static List<String> segments(final Path relative) {
            return StreamSupport.stream(relative.spliterator(), false).map(Path::toString).filter(segment -> !segment.isEmpty()).toList();
        }

        private static JavaLauncher java25(final JavaToolchainService toolchains, final String foojayVersion) {
            try {
                return toolchains.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(ENGINE_JAVA_VERSION))).get();
            } catch (final RuntimeException e) {
                throw new GradleException("""
                                          javafmt needs a Java 25 toolchain to run on, and Gradle found none.
                                          Either install a JDK 25, or let Gradle download one by adding
                                              id("org.gradle.toolchains.foojay-resolver-convention") version "%s"
                                          to the plugins block of settings.gradle.kts.""".formatted(foojayVersion), e);
            }
        }
    }
}
