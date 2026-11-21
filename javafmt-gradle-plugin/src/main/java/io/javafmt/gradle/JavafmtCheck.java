package io.javafmt.gradle;

import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@CacheableTask
public abstract class JavafmtCheck extends JavafmtTask {

    @OutputFile
    public abstract RegularFileProperty getMarker();

    @TaskAction
    public void check(final InputChanges changes) {
        final var files = checksOnlyChangedFiles(changes)
                ? changedFiles(changes)
                : getSources().getFiles();
        submit(files, false, Optional.of(getMarker().get().getAsFile()));
    }

    private boolean checksOnlyChangedFiles(final InputChanges changes) {
        return changes.isIncremental()
            && !getAnalyze().get()
            && !getRatchetFrom().isPresent();
    }

    private Set<File> changedFiles(final InputChanges changes) {
        return StreamSupport.stream(changes.getFileChanges(getSources()).spliterator(), false)
                .filter(change -> change.getFileType() == FileType.FILE && change.getChangeType() != ChangeType.REMOVED)
                .map(FileChange::getFile)
                .collect(Collectors.toUnmodifiableSet());
    }
}
