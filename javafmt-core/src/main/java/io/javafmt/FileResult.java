package io.javafmt;

import java.io.IOException;
import java.nio.file.Path;

public sealed interface FileResult permits FileResult.UpToDate, FileResult.Unreadable, FileResult.Formatted {

    Path path();

    record UpToDate(Path path) implements FileResult {}

    record Unreadable(Path path, IOException error) implements FileResult {}

    record Formatted(Path path, String source, FormatResult result) implements FileResult {

        public boolean changed() {
            return !result.output().equals(source);
        }
    }
}
