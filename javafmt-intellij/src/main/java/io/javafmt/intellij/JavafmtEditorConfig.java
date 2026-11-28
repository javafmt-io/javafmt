package io.javafmt.intellij;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The {@code .editorconfig} that tells every editor the indentation and width javafmt formats Java to. */
@UtilityClass
final class JavafmtEditorConfig {

    static final String FILE_NAME = ".editorconfig";

    static final String CONTENT = """
            [*.java]
            indent_style = space
            indent_size = 4
            tab_width = 4
            max_line_length = 150
            charset = utf-8
            insert_final_newline = true
            trim_trailing_whitespace = true
            ij_continuation_indent_size = 8
            ij_wrap_on_typing = false
            """;

    /** Writes the file into {@code directory} unless one is already there, and says whether it did. */
    boolean createIfMissing(final Path directory) {
        try {
            Files.writeString(directory.resolve(FILE_NAME), CONTENT, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return true;
        } catch (final FileAlreadyExistsException _) {
            return false;
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
