package io.javafmt.intellij;

import java.nio.file.Path;
import java.util.Objects;

sealed interface Installation {

    record Installed(Path executable) implements Installation {

        public Installed {
            Objects.requireNonNull(executable, "executable");
        }
    }

    record Failed(String reason) implements Installation {

        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Cancelled() implements Installation {}
}
