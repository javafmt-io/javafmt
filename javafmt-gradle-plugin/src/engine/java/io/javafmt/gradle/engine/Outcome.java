package io.javafmt.gradle.engine;

import java.util.List;
import java.util.Objects;

public record Outcome(List<Source> changed, List<Finding> errors, List<Finding> warnings) {

    public Outcome {
        changed = List.copyOf(changed);
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public record Source(String path, String display) {

        public Source {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(display, "display");
        }
    }

    public record Finding(Source source, int line, int column, String message, String description) {

        public Finding {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(description, "description");
        }
    }
}
