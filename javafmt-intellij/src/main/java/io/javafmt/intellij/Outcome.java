package io.javafmt.intellij;

import io.javafmt.daemon.protocol.Diagnostic;
import io.javafmt.daemon.protocol.Kind;
import io.javafmt.daemon.protocol.Severity;

import java.util.List;
import java.util.Objects;

/** What one format attempt produced. */
public sealed interface Outcome {

    /**
     * {@code output} is the text to install. javafmt returns the input unchanged when it cannot
     * format, so this is always safe to put in the document.
     */
    record Formatted(String output, List<Diagnostic> diagnostics) implements Outcome {

        public Formatted {
            Objects.requireNonNull(output, "output");
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean hasInternalError() {
            return diagnostics.stream()
                    .anyMatch(diagnostic -> diagnostic.getKind() == Kind.KIND_INTERNAL);
        }

        public boolean hasFailure() {
            return diagnostics.stream()
                    .anyMatch(diagnostic -> diagnostic.getSeverity() != Severity.SEVERITY_WARNING);
        }
    }

    record Cancelled() implements Outcome {}

    /** The binary could not be run at all. {@code internal} distinguishes a javafmt bug. */
    record Refused(String reason, boolean internal) implements Outcome {

        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
