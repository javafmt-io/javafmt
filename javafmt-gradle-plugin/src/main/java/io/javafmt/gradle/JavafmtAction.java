package io.javafmt.gradle;

import io.javafmt.gradle.engine.JavafmtEngine;
import io.javafmt.gradle.engine.Outcome;
import io.javafmt.gradle.engine.Request;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

public abstract class JavafmtAction implements WorkAction<JavafmtAction.Parameters> {

    private static final Logger LOGGER = Logging.getLogger(JavafmtAction.class);

    private static final ProblemGroup GROUP = ProblemGroup.create("javafmt", "javafmt");

    private static final ProblemId UNFORMATTED = ProblemId.create("unformatted-file", "File not formatted by javafmt", GROUP);

    private static final ProblemId ERROR = ProblemId.create("error", "javafmt error", GROUP);

    private static final ProblemId WARNING = ProblemId.create("warning", "javafmt warning", GROUP);

    @Inject
    protected abstract Problems getProblems();

    @Override
    public void execute() {
        final var parameters = getParameters();
        final var write = parameters.getWrite().get();
        final var files = List.copyOf(parameters.getFiles().getFiles());
        LOGGER.info("javafmt: processed {} file(s)", files.size());
        final var outcome = JavafmtEngine.run(
                new Request(
                        files,
                        parameters.getAnalyze().get() ? Optional.of(parameters.getAnalysisClasspath().get()) : Optional.empty(),
                        write,
                        parameters.getProjectDirectory().get().getAsFile(),
                        parameters.getThreads().get()));
        final var reporter = getProblems().getReporter();
        outcome.warnings().forEach(warning -> LOGGER.warn("javafmt: {}", warning.description()));
        reporter.report(outcome.warnings().stream().map(warning -> problem(reporter, WARNING, warning)).toList());
        if (write) {
            outcome.changed().forEach(file -> LOGGER.info("javafmt: formatted {}", file.display()));
        }
        final var formatTaskPath = parameters.getFormatTaskPath().get();
        final var unformatted = write ? List.<Outcome.Source>of() : outcome.changed();
        final var message = Stream.concat(unformatted(unformatted, formatTaskPath), outcome.errors().stream().map(Outcome.Finding::description))
                .collect(Collectors.joining("\n"));
        if (!message.isEmpty()) {
            throw reporter.throwing(
                    new GradleException(message),
                    Stream.concat(
                            unformatted.stream().map(source -> unformattedProblem(reporter, source, formatTaskPath)),
                            outcome.errors().stream().map(error -> problem(reporter, ERROR, error)))
                            .toList());
        }
        if (parameters.getMarker().isPresent()) {
            try {
                Files.writeString(parameters.getMarker().get().getAsFile().toPath(), "");
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Stream<String> unformatted(final List<Outcome.Source> unformatted, final String formatTaskPath) {
        if (unformatted.isEmpty()) {
            return Stream.empty();
        }
        final var header = "javafmt: " + unformatted.size() + " file(s) not formatted, run 'gradle " + formatTaskPath + "' to fix:";
        return Stream.concat(Stream.of(header), unformatted.stream().map(file -> "  " + file.display()));
    }

    private static Problem unformattedProblem(final ProblemReporter reporter, final Outcome.Source source, final String formatTaskPath) {
        return reporter.create(UNFORMATTED, spec -> spec.fileLocation(source.path()).solution("Run 'gradle " + formatTaskPath + "'"));
    }

    private static Problem problem(final ProblemReporter reporter, final ProblemId id, final Outcome.Finding finding) {
        return reporter.create(
                id,
                spec -> {
                    if (finding.line() > 0) {
                        spec.lineInFileLocation(finding.source().path(), finding.line(), finding.column());
                    } else {
                        spec.fileLocation(finding.source().path());
                    }
                    spec.contextualLabel(finding.message()).details(finding.description());
                });
    }

    public interface Parameters extends WorkParameters {

        ConfigurableFileCollection getFiles();

        Property<Boolean> getAnalyze();

        Property<Integer> getThreads();

        Property<String> getAnalysisClasspath();

        Property<Boolean> getWrite();

        DirectoryProperty getProjectDirectory();

        Property<String> getFormatTaskPath();

        RegularFileProperty getMarker();
    }
}
