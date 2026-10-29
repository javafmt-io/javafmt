package io.javafmt.lint.oracle;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AbstractFileSetCheck;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a single Checkstyle check against an in-memory Java source string and returns the
 * violations it would report. Used as an independent oracle to validate javafmt's lint rules:
 * if javafmt claims a file is clean, Checkstyle should agree.
 *
 * <p>Each call configures a fresh {@link Checker} with a {@code TreeWalker} containing one
 * check module. Source is written to a temp file because Checkstyle's public process API
 * takes {@link File} paths.
 *
 * <p>A check that extends {@link AbstractFileSetCheck} — {@code NewlineAtEndOfFileCheck}, for one —
 * inspects raw file contents rather than a syntax tree and has to hang off the {@code Checker}
 * directly; Checkstyle rejects it as a {@code TreeWalker} child. The placement is derived from the
 * check class so callers pass a class name either way.
 */
public final class CheckstyleOracle {

    private static final Logger LOG = LoggerFactory.getLogger(CheckstyleOracle.class);

    /**
     * @param source           Java source to check.
     * @param checkClassName   fully-qualified Checkstyle Check class (e.g.
     *                         {@code com.puppycrawl.tools.checkstyle.checks.coding.FinalLocalVariableCheck}).
     * @param checkProperties  properties to set on the check module (Checkstyle property names).
     */
    public static List<Violation> run(final String source, final String checkClassName, final Map<String, String> checkProperties) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(checkClassName, "checkClassName");
        Objects.requireNonNull(checkProperties, "checkProperties");
        final var check = new DefaultConfiguration(checkClassName);
        checkProperties.forEach(check::addProperty);
        final var root = new DefaultConfiguration("Checker");
        root.addProperty("charset", "UTF-8");
        if (isFileSetCheck(checkClassName)) {
            root.addChild(check);
        } else {
            final var treeWalker = new DefaultConfiguration("TreeWalker");
            treeWalker.addChild(check);
            root.addChild(treeWalker);
        }
        final var checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(root);
        } catch (CheckstyleException e) {
            throw new IllegalStateException("failed to configure Checkstyle", e);
        }
        final var collector = new CollectingListener();
        checker.addListener(collector);
        final Path tempFile;
        try {
            tempFile = Files.createTempFile("javafmt-oracle-", ".java");
            Files.writeString(tempFile, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            checker.process(List.of(tempFile.toFile()));
        } catch (CheckstyleException e) {
            throw new IllegalStateException("Checkstyle processing failed", e);
        } finally {
            checker.destroy();
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException error) {
                LOG.debug("failed to delete temp file {}", tempFile, error);
            }
        }
        if (collector.failure != null) {
            throw new IllegalStateException("Checkstyle reported an exception", collector.failure);
        }
        return List.copyOf(collector.violations);
    }

    private static boolean isFileSetCheck(final String checkClassName) {
        try {
            return AbstractFileSetCheck.class.isAssignableFrom(Class.forName(checkClassName, false, Checker.class.getClassLoader()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("unknown Checkstyle check class: " + checkClassName, e);
        }
    }

    public record Violation(int line, int column, String message) {

        public Violation {
            Objects.requireNonNull(message, "message");
        }
    }

    private static final class CollectingListener implements AuditListener {

        private final List<Violation> violations = new ArrayList<>();

        @Nullable private Throwable failure;

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
