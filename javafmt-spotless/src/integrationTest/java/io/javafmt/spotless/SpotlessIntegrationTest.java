package io.javafmt.spotless;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Fixtures;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a real Gradle build that wires javafmt into Spotless the way {@code docs/intro.md} tells
 * users to.
 *
 * <p>Every other test in this module calls {@code step.format(...)} directly, which says nothing
 * about the part users actually depend on: that the step reaches a build script at all, that
 * Spotless's up-to-date check keys on the javafmt version, and that none of it breaks under the
 * configuration cache.
 *
 * <p>The generated build resolves nothing from a repository. javafmt, its dependencies and the
 * Spotless plugin are all injected as files on the buildscript classpath, so the build is offline
 * and pinned to exactly the code under test rather than to whatever is published.
 */
class SpotlessIntegrationTest {

    /** Formats cleanly but trips the warning-only {@code AvoidStarImport} rule. */
    private static final String STAR_IMPORT_SOURCE = "import java.util.*;\n\nclass Star {\n\n    List<String> xs;\n}\n";

    private static final String DEFAULT_STEP = "io.javafmt.spotless.JavafmtFormatterStep.create()";

    /** Takes its version from a project property, so a run can pretend javafmt was upgraded. */
    private static final String PINNED_STEP = "io.javafmt.spotless.VersionPinnedSteps.create(project.findProperty('javafmtVersion') ?: '1.0.0')";

    @Test
    void spotlessApplyFormatsJavaSourcesWithJavafmt(@TempDir final Path dir) throws IOException {
        writeProject(dir, DEFAULT_STEP);
        final var source = writeSource(dir, "Foo.java", Fixtures.UNFORMATTED_FOO);
        final var result = build(dir, "spotlessApply");
        assertThat(outcomeOf(result, ":spotlessJavaApply")).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(source)).isEqualTo(Fixtures.FORMATTED_FOO);
    }

    @Test
    void aSecondRunOverUnchangedSourcesIsUpToDate(@TempDir final Path dir) throws IOException {
        // Already formatted, so the first run leaves the file byte for byte as it was and the
        // second run has nothing new to look at.
        writeProject(dir, DEFAULT_STEP);
        writeSource(dir, "Foo.java", Fixtures.FORMATTED_FOO);
        build(dir, "spotlessApply");
        assertThat(outcomeOf(build(dir, "spotlessApply"), ":spotlessJava")).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void upgradingJavafmtInvalidatesTheUpToDateState(@TempDir final Path dir) throws IOException {
        // This is the whole reason the docs lead with create() rather than custom(): a custom
        // step's up-to-date key is a hand-maintained integer, so a javafmt upgrade would leave
        // every project passing spotlessCheck on formatting the new release no longer produces.
        writeProject(dir, PINNED_STEP);
        writeSource(dir, "Foo.java", Fixtures.FORMATTED_FOO);
        build(dir, "spotlessApply", "-PjavafmtVersion=1.0.0");
        assertThat(outcomeOf(build(dir, "spotlessApply", "-PjavafmtVersion=1.0.0"), ":spotlessJava")).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(outcomeOf(build(dir, "spotlessApply", "-PjavafmtVersion=2.0.0"), ":spotlessJava")).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void theStepWorksWithTheConfigurationCache(@TempDir final Path dir) throws IOException {
        // The step is stored in the configuration cache along with the task that holds it, so a
        // component that cannot be serialized shows up here as a configuration cache problem
        // rather than as a formatting bug.
        writeProject(dir, DEFAULT_STEP);
        final var source = writeSource(dir, "Foo.java", Fixtures.FORMATTED_FOO);
        final var stored = build(dir, "spotlessApply", "--configuration-cache");
        assertThat(stored.getOutput())
                .contains("Configuration cache entry stored.")
                .doesNotContain("problems were found");
        assertThat(Files.readString(source)).isEqualTo(Fixtures.FORMATTED_FOO);
        assertThat(build(dir, "spotlessApply", "--configuration-cache").getOutput()).contains("Reusing configuration cache");
    }

    @Test
    void spotlessCheckSurfacesJavafmtsWarningOnlyRules(@TempDir final Path dir) throws IOException {
        writeProject(dir, DEFAULT_STEP);
        writeSource(dir, "Star.java", STAR_IMPORT_SOURCE);
        final var result = buildAndFail(dir, "spotlessCheck");
        assertThat(result.getOutput()).contains("star import");
    }

    @Test
    void spotlessTargetExcludeKeepsAFileFromBeingFormattedOrLinted(@TempDir final Path dir) throws IOException {
        writeProject(dir, DEFAULT_STEP, "targetExclude 'src/**/Generated.java'");
        final var unformattedWithStarImport = "import java.util.*;\nclass Generated { List<String> xs; }\n";
        final var generated = writeSource(dir, "Generated.java", unformattedWithStarImport);
        build(dir, "spotlessApply");
        build(dir, "spotlessCheck");
        assertThat(Files.readString(generated)).isEqualTo(unformattedWithStarImport);
    }

    private static BuildResult build(final Path dir, final String... arguments) {
        return runner(dir, arguments).build();
    }

    private static BuildResult buildAndFail(final Path dir, final String... arguments) {
        return runner(dir, arguments).buildAndFail();
    }

    private static GradleRunner runner(final Path dir, final String... arguments) {
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .forwardOutput()
                .withArguments(arguments);
    }

    private static TaskOutcome outcomeOf(final BuildResult result, final String taskPath) {
        final var task = result.task(taskPath);
        assertThat(task).as("task %s did not run", taskPath).isNotNull();
        return task.getOutcome();
    }

    private static Path writeSource(final Path dir, final String name, final String content) throws IOException {
        final var source = dir.resolve("src/main/java").resolve(name);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    /** Writes the build exactly as {@code docs/intro.md} describes it, bar the injected classpath. */
    private static void writeProject(final Path dir, final String stepExpression) throws IOException {
        writeProject(dir, stepExpression, "");
    }

    private static void writeProject(final Path dir, final String stepExpression, final String targetExclude) throws IOException {
        Files.writeString(dir.resolve("settings.gradle"), "rootProject.name = 'javafmt-spotless-it'\n");
        Files.writeString(dir.resolve("build.gradle"), """
                                                       buildscript {
                                                           dependencies {
                                                               classpath files(%s)
                                                           }
                                                       }
                                       
                                                       apply plugin: 'java'
                                                       apply plugin: 'com.diffplug.spotless'
                                       
                                                       spotless {
                                                           java {
                                                               target 'src/**/*.java'
                                                               %s
                                                               addStep %s
                                                           }
                                                       }
                                                       """.formatted(generatedBuildClasspath(), targetExclude, stepExpression));
    }

    /** The injected classpath as a Groovy argument list of quoted paths. */
    private static String generatedBuildClasspath() {
        final var raw = System.getProperty("javafmt.generatedBuildClasspath");
        assertThat(raw)
                .as("the integrationTest task must pass javafmt.generatedBuildClasspath")
                .isNotBlank();
        return Arrays.stream(raw.split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(entry -> "'" + entry.replace("\\", "\\\\") + "'")
                .collect(Collectors.joining(", "));
    }
}
