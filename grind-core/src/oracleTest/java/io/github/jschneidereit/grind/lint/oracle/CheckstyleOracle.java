package io.github.jschneidereit.grind.lint.oracle;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Runs a single Checkstyle check against an in-memory Java source string and returns the
 * violations it would report. Used as an independent oracle to validate grind's lint rules:
 * if grind claims a file is clean, Checkstyle should agree.
 *
 * <p>Each call configures a fresh {@link Checker} with a {@code TreeWalker} containing one
 * check module. Source is written to a temp file because Checkstyle's public process API
 * takes {@link File} paths.
 */
public final class CheckstyleOracle {

    /**
     * @param source           Java source to check.
     * @param checkClassName   fully-qualified Checkstyle Check class (e.g.
     *                         {@code com.puppycrawl.tools.checkstyle.checks.coding.FinalLocalVariableCheck}).
     * @param checkProperties  properties to set on the check module (Checkstyle property names).
     */
    public static List<Violation> run(
            final String source,
            final String checkClassName,
            final Map<String, String> checkProperties) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(checkClassName, "checkClassName");
        Objects.requireNonNull(checkProperties, "checkProperties");

        final var check = new DefaultConfiguration(checkClassName);
        checkProperties.forEach(check::addProperty);

        final var treeWalker = new DefaultConfiguration("TreeWalker");
        treeWalker.addChild(check);

        final var root = new DefaultConfiguration("Checker");
        root.addProperty("charset", "UTF-8");
        root.addChild(treeWalker);

        final var checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(root);
        } catch (final CheckstyleException e) {
            throw new IllegalStateException("failed to configure Checkstyle", e);
        }

        final var collector = new CollectingListener();
        checker.addListener(collector);

        final Path tempFile;
        try {
            tempFile = Files.createTempFile("grind-oracle-", ".java");
            Files.writeString(tempFile, source);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            checker.process(List.of(tempFile.toFile()));
        } catch (final CheckstyleException e) {
            throw new IllegalStateException("Checkstyle processing failed", e);
        } finally {
            checker.destroy();
            try {
                Files.deleteIfExists(tempFile);
            } catch (final IOException ignored) {
                // best-effort cleanup
            }
        }

        if (collector.failure != null) {
            throw new IllegalStateException("Checkstyle reported an exception", collector.failure);
        }
        return List.copyOf(collector.violations);
    }

    public record Violation(int line, int column, String message) {

        public Violation {
            Objects.requireNonNull(message, "message");
        }
    }

    private static final class CollectingListener implements AuditListener {

        private final List<Violation> violations = new ArrayList<>();

        private @Nullable Throwable failure;

        @Override
        public void addError(final AuditEvent event) {
            violations.add(new Violation(event.getLine(), event.getColumn(), event.getMessage()));
        }

        @Override
        public void addException(final AuditEvent event, final Throwable throwable) {
            failure = throwable;
        }

        @Override
        public void auditStarted(final AuditEvent event) {}

        @Override
        public void auditFinished(final AuditEvent event) {}

        @Override
        public void fileStarted(final AuditEvent event) {}

        @Override
        public void fileFinished(final AuditEvent event) {}
    }

    private CheckstyleOracle() {}
}
