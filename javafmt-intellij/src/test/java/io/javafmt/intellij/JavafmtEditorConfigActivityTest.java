package io.javafmt.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JavafmtEditorConfigActivityTest extends JavafmtIntellijTestCase {

    @Test
    public void testCreatesTheFileAndSaysSoWhenJavafmtIsEnabled(@TempDir final Path directory) {
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        JavafmtEditorConfigActivity.createIfEnabled(getProject(), directory);
        assertThat(directory.resolve(".editorconfig")).hasContent(JavafmtEditorConfig.CONTENT);
        assertThat(notifications)
                .singleElement()
                .satisfies(notification -> assertThat(notification.getContent()).contains(".editorconfig"));
    }

    @Test
    public void testDoesNothingWhenJavafmtIsDisabled(@TempDir final Path directory) {
        JavafmtEditorConfigActivity.createIfEnabled(getProject(), directory);
        assertThat(directory.resolve(".editorconfig")).doesNotExist();
        assertThat(notifications).isEmpty();
    }

    @Test
    public void testStaysSilentWhenTheFileAlreadyExists(@TempDir final Path directory) throws IOException {
        Files.writeString(directory.resolve(".editorconfig"), "root = true\n");
        JavafmtSettings.getInstance(getProject()).setEnabled(true);
        JavafmtEditorConfigActivity.createIfEnabled(getProject(), directory);
        assertThat(directory.resolve(".editorconfig")).hasContent("root = true\n");
        assertThat(notifications).isEmpty();
    }
}
