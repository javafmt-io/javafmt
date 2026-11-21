package io.javafmt.intellij;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

sealed interface BinaryLookup {

    record Found(BinaryIdentity binary) implements BinaryLookup {

        public Found {
            Objects.requireNonNull(binary, "binary");
        }
    }

    record MissingAt(String configuredPath) implements BinaryLookup {

        public MissingAt {
            Objects.requireNonNull(configuredPath, "configuredPath");
        }
    }

    record Pending(CompletableFuture<Installation> download) implements BinaryLookup {

        public Pending {
            Objects.requireNonNull(download, "download");
        }
    }
}
