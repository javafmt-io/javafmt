package io.javafmt.gradle;

import java.util.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "It rewrites its own input files in place.")
public abstract class JavafmtFormat extends JavafmtTask {

    @TaskAction
    public void format() {
        submit(getSources().getFiles(), true, Optional.empty());
    }
}
