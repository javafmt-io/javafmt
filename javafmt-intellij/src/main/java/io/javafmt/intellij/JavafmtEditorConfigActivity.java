package io.javafmt.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/** Gives a project that uses javafmt an {@code .editorconfig} stating the indentation and width it formats to. */
public final class JavafmtEditorConfigActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull final Project project, @NotNull final Continuation<? super Unit> continuation) {
        createIfEnabled(project);
        return Unit.INSTANCE;
    }

    static void createIfEnabled(final Project project) {
        Optional.ofNullable(ProjectUtil.guessProjectDir(project))
                .filter(directory -> LocalFileSystem.getInstance().equals(directory.getFileSystem()))
                .map(VirtualFile::toNioPath)
                .ifPresent(directory -> createIfEnabled(project, directory));
    }

    static void createIfEnabled(final Project project, final Path directory) {
        if (!JavafmtSettings.getInstance(project).isEnabled() || !JavafmtEditorConfig.createIfMissing(directory)) {
            return;
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory.resolve(JavafmtEditorConfig.FILE_NAME));
        NotificationGroupManager.getInstance()
                .getNotificationGroup("javafmt")
                .createNotification("javafmt",
                        "Created .editorconfig so every editor indents Java the way javafmt formats it.", NotificationType.INFORMATION)
                .notify(project);
    }
}
