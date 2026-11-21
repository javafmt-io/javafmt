package io.javafmt.gradle;

import io.javafmt.gradle.engine.JavafmtEngine;
import io.javafmt.gradle.engine.Outcome;
import io.javafmt.gradle.engine.Request;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

public abstract class JavafmtAction implements WorkAction<JavafmtAction.Parameters> {

    private static final Logger LOGGER = Logging.getLogger(JavafmtAction.class);

    @Inject
    protected abstract Problems getProblems();

    @Override
    public void execute() {
        final var parameters = getParameters();
        final var write = parameters.getWrite().get();
        final var files = List.copyOf(parameters.getFiles().getFiles());
        LOGGER.info("javafmt: processed {} file(s)", files.size());
        final var outcome = JavafmtEngine.run(request(parameters, files));
        final var problems = getProblems();
        final var reporter = problems.getReporter();
        final var group = problems.getGroups()
                .getVerification()
                .getCodeQuality()
                .group("javafmt");
        final var ids = new ProblemIds(
                group.problemId("javafmt warning"), group.problemId("File not formatted by javafmt"), group.problemId("javafmt error"));
        reportWarnings(reporter, ids.warning(), outcome.warnings());
        if (write) {
            outcome.changed().forEach(file -> LOGGER.info("javafmt: formatted {}", file.display()));
        }
        final var unformatted = write ? List.<Outcome.Source>of() : outcome.changed();
        if (!unformatted.isEmpty() || !outcome.errors().isEmpty()) {
            throw failure(reporter, ids, parameters.getFormatTaskPath().get(), unformatted, outcome.errors());
        }
        writeMarker(parameters);
    }

    private static Request request(final Parameters parameters, final List<File> files) {
        return new Request(
                files,
                Optional.ofNullable(parameters.getAnalysisClasspath().getOrNull()),
                Optional.ofNullable(parameters.getRatchetFrom().getOrNull()),
                parameters.getWrite().get(),
                parameters.getProjectDirectory().get().getAsFile(),
                parameters.getThreads().get());
    }

    private static void reportWarnings(final ProblemReporter reporter, final ProblemId id, final List<Outcome.Finding> warnings) {
        warnings.forEach(warning -> LOGGER.warn("javafmt: {}", warning.description()));
        reporter.report(warnings.stream()
                .map(warning -> problem(reporter, id, warning))
                .toList());
    }

    private static RuntimeException failure(final ProblemReporter reporter,
                                            final ProblemIds ids,
                                            final String formatTaskPath,
                                            final List<Outcome.Source> unformatted,
                                            final List<Outcome.Finding> errors) {
        final var message = Stream.concat(
                unformattedLines(unformatted, formatTaskPath),
                errors.stream()
                        .map(Outcome.Finding::description))
                .collect(Collectors.joining("\n"));
        return reporter.throwing(
                new GradleException(message),
                Stream.concat(
                        unformatted.stream()
                                .map(source -> unformattedProblem(reporter, ids.unformatted(), source, formatTaskPath)),
                        errors.stream()
                                .map(error -> problem(reporter, ids.error(), error)))
                        .toList());
    }

    private static void writeMarker(final Parameters parameters) {
        if (parameters.getMarker().isPresent()) {
            try {
                Files.writeString(
                        parameters.getMarker().get().getAsFile().toPath(), "");
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Stream<String> unformattedLines(final List<Outcome.Source> unformatted, final String formatTaskPath) {
        if (unformatted.isEmpty()) {
            return Stream.empty();
        }
        final var header = "javafmt: " + unformatted.size() + " file(s) not formatted, run 'gradle " + formatTaskPath + "' to fix:";
        return Stream.concat(Stream.of(header), unformatted.stream()
                .map(file -> "  " + file.display()));
    }

    private static Problem unformattedProblem(final ProblemReporter reporter,
                                              final ProblemId id,
                                              final Outcome.Source source,
                                              final String formatTaskPath) {
        return reporter.create(id, spec -> spec.fileLocation(source.path()).solution("Run 'gradle " + formatTaskPath + "'"));
    }

    private static Problem problem(final ProblemReporter reporter, final ProblemId id, final Outcome.Finding finding) {
        return reporter.create(id, spec -> {
            if (finding.line() > 0) {
                spec.lineInFileLocation(finding.source().path(), finding.line(), finding.column());
            } else {
                spec.fileLocation(finding.source().path());
            }
            spec.contextualLabel(finding.message()).details(finding.description());
        });
    }

    private record ProblemIds(ProblemId warning, ProblemId unformatted, ProblemId error) {}

    public interface Parameters extends WorkParameters {

        ConfigurableFileCollection getFiles();

        Property<String> getRatchetFrom();

        Property<Integer> getThreads();

        Property<String> getAnalysisClasspath();

        Property<Boolean> getWrite();

        DirectoryProperty getProjectDirectory();

        Property<String> getFormatTaskPath();

        RegularFileProperty getMarker();
    }
}
