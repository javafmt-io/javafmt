package io.javafmt.gradle;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;

public record ProjectRelativeExcludes(List<String> sourceDirectory, List<String> patterns) implements Spec<FileTreeElement> {

    public ProjectRelativeExcludes {
        sourceDirectory = List.copyOf(sourceDirectory);
        patterns = List.copyOf(patterns);
    }

    @Override
    public boolean isSatisfiedBy(final FileTreeElement element) {
        return !patterns.isEmpty()
            && new PatternSet().include(patterns)
                    .getAsSpec()
                    .isSatisfiedBy(new ProjectRelativeElement(element, element.getRelativePath().prepend(sourceDirectory.toArray(String[]::new))));
    }

    private record ProjectRelativeElement(FileTreeElement element, RelativePath relativePath) implements FileTreeElement {

        @Override
        public File getFile() {
            return element.getFile();
        }

        @Override
        public boolean isDirectory() {
            return element.isDirectory();
        }

        @Override
        public long getLastModified() {
            return element.getLastModified();
        }

        @Override
        public long getSize() {
            return element.getSize();
        }

        @Override
        public InputStream open() {
            return element.open();
        }

        @Override
        public void copyTo(final OutputStream output) {
            element.copyTo(output);
        }

        @Override
        public boolean copyTo(final File target) {
            return element.copyTo(target);
        }

        @Override
        public String getName() {
            return element.getName();
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
            return element.getPermissions();
        }
    }
}
