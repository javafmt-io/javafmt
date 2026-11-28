package io.javafmt.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavafmtEditorConfigTest {

    @Test
    void createsTheFileWhenTheDirectoryHasNone(@TempDir final Path directory) {
        assertThat(JavafmtEditorConfig.createIfMissing(directory)).isTrue();
        assertThat(directory.resolve(".editorconfig")).hasContent(JavafmtEditorConfig.CONTENT);
    }

    @Test
    void neverOverwritesAnExistingFile(@TempDir final Path directory) throws IOException {
        final var existing = directory.resolve(".editorconfig");
        Files.writeString(existing, "root = true\n");
        assertThat(JavafmtEditorConfig.createIfMissing(directory)).isFalse();
        assertThat(existing).hasContent("root = true\n");
    }

    @Test
    void sectionMatchesWhatJavafmtProduces() {
        assertThat(JavafmtEditorConfig.CONTENT)
                .contains("[*.java]")
                .contains("indent_style = space")
                .contains("indent_size = 4")
                .contains("max_line_length = 150")
                .contains("ij_continuation_indent_size = 8")
                .contains("ij_wrap_on_typing = false")
                .endsWith("\n");
    }
}
