package io.javafmt.gradle;

import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRelativeExcludesTest {

    @Test
    void emptyPatternsExcludeNothing() {
        final var excludes = new ProjectRelativeExcludes(List.of("src", "main", "java"), List.of());
        assertThat(excludes.isSatisfiedBy(element("Foo.java"))).isFalse();
    }

    @Test
    void aPatternRelativeToTheProjectMatchesASourceOutsideIt() {
        final var excludes = new ProjectRelativeExcludes(List.of("..", "shared"), List.of("../shared/gen/**"));
        assertThat(excludes.isSatisfiedBy(element("gen", "Generated.java"))).isTrue();
        assertThat(excludes.isSatisfiedBy(element("Shared.java"))).isFalse();
    }

    @Test
    void aSourceDirectoryEqualToTheProjectDirectoryAddsNoSegment() {
        final var excludes = new ProjectRelativeExcludes(List.of(), List.of("gen/**"));
        assertThat(excludes.isSatisfiedBy(element("gen", "Generated.java"))).isTrue();
        assertThat(excludes.isSatisfiedBy(element("Other.java"))).isFalse();
    }

    private static FileTreeElement element(final String... segments) {
        return new StubElement(new RelativePath(true, segments));
    }

    private record StubElement(RelativePath relativePath) implements FileTreeElement {

        @Override
        public File getFile() {
            return new File(relativePath.getPathString());
        }

        @Override
        public boolean isDirectory() {
            return !relativePath.isFile();
        }

        @Override
        public long getLastModified() {
            return 0;
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public InputStream open() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void copyTo(final OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean copyTo(final File target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            return relativePath.getLastName();
        }

        @Override
        public String getPath() {
            return relativePath.getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            return relativePath;
        }

        @Override
        public FilePermissions getPermissions() {
            throw new UnsupportedOperationException();
        }
    }
}
