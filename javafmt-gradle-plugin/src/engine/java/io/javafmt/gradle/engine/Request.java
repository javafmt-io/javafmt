package io.javafmt.gradle.engine;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Request(List<File> files,
                      Optional<String> analysisClasspath,
                      Optional<String> ratchetFrom,
                      boolean write,
                      File projectDirectory,
                      int threads) {
    public Request {
        files = List.copyOf(files);
        Objects.requireNonNull(analysisClasspath, "analysisClasspath");
        Objects.requireNonNull(ratchetFrom, "ratchetFrom");
        Objects.requireNonNull(projectDirectory, "projectDirectory");
    }
}
