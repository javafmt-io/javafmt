package io.javafmt.gradle;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

@CacheableTask
public abstract class JavafmtCheck extends JavafmtTask {

    @OutputFile
    public abstract RegularFileProperty getMarker();

    @TaskAction
    public void check(final InputChanges changes) {
        final var files = changes.isIncremental() && !getAnalyze().get()
                ? StreamSupport.stream(changes.getFileChanges(getSources()).spliterator(), false)
                        .filter(change -> change.getFileType() == FileType.FILE && change.getChangeType() != ChangeType.REMOVED)
                        .map(FileChange::getFile)
                        .collect(Collectors.toSet())
                : getSources().getFiles();
        submit(files, false, Optional.of(getMarker().get().getAsFile()));
    }
}
