package io.javafmt.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

record TestBuild(Path dir) {

    static final String JAVA25_HOME = System.getProperty("javafmt.test.java25Home", "");

    static final String OLDER_JAVA_HOME = System.getProperty("javafmt.test.olderJavaHome", "");

    static final String MINIMUM_GRADLE_VERSION = System.getProperty("javafmt.test.minimumGradleVersion", "");

    private static final Optional<String> COVERAGE_AGENT = ManagementFactory.getRuntimeMXBean()
            .getInputArguments()
            .stream()
            .filter(argument -> argument.startsWith("-javaagent:") && argument.contains("jacocoagent"))
            .findFirst()
            .map(
                    agent -> Pattern.compile("destfile=([^,]+)")
                            .matcher(agent)
                            .replaceFirst(match -> Matcher.quoteReplacement("destfile=" + Path.of(match.group(1)).toAbsolutePath())));

    TestBuild {
        Objects.requireNonNull(dir, "dir");
    }

    static TestBuild singleProject(final Path dir, final String extraBuildScript) {
        final var build = new TestBuild(dir);
        build.write("settings.gradle", "rootProject.name = 'demo'\n");
        build.write("build.gradle", """
                plugins {
                    id 'java'
                    id 'io.javafmt'
                }
                """ + COVERAGE_AGENT.map(agent -> "javafmt { jvmArgs.add('" + agent + "') }\n").orElse("") + extraBuildScript);
        build.gradleProperties(List.of());
        return build;
    }

    void gradleProperties(final List<String> extra) {
        final var lines = Stream.concat(
                Stream.of(
                        "org.gradle.configuration-cache=true",
                        "org.gradle.isolated-projects=true",
                        "org.gradle.java.installations.auto-detect=false",
                        "org.gradle.java.installations.auto-download=false",
                        "org.gradle.java.installations.paths=" + JAVA25_HOME),
                Stream.concat(
                        COVERAGE_AGENT.map(agent -> "org.gradle.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=384m " + agent).stream(),
                        extra.stream()));
        write("gradle.properties", lines.collect(Collectors.joining("\n", "", "\n")));
    }

    Path write(final String relative, final String content) {
        final var path = dir.resolve(relative);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return path;
    }

    void move(final String from, final String to) {
        try {
            Files.move(dir.resolve(from), dir.resolve(to));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void delete(final String relative) {
        try {
            Files.delete(dir.resolve(relative));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String read(final String relative) {
        try {
            return Files.readString(dir.resolve(relative));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    BuildResult run(final String... arguments) {
        return runner(arguments).build();
    }

    BuildResult fail(final String... arguments) {
        return runner(arguments).buildAndFail();
    }

    BuildResult failOn(final String gradleVersion, final String... arguments) {
        return runner(arguments).withGradleVersion(gradleVersion).buildAndFail();
    }

    private GradleRunner runner(final String... arguments) {
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withEnvironment(
                        System.getenv()
                                .entrySet()
                                .stream()
                                .filter(variable -> !variable.getKey().startsWith("JAVA_HOME"))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .withArguments(Stream.concat(Stream.of("--stacktrace", "--no-watch-fs"), Stream.of(arguments)).toList())
                .forwardOutput();
    }
}
