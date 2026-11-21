package io.javafmt.intellij;

import java.util.Objects;
import java.util.Optional;

public sealed interface VersionProbe {

    default Optional<String> reportedVersion() {
        return this instanceof Reported reported ? Optional.of(reported.output()) : Optional.empty();
    }

    record Reported(String output) implements VersionProbe {

        public Reported {
            Objects.requireNonNull(output, "output");
        }
    }

    record Failed() implements VersionProbe {}

    record Inconclusive() implements VersionProbe {}
}
