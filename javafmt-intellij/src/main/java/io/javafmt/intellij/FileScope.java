package io.javafmt.intellij;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import io.javafmt.JavafmtConfig;
import io.javafmt.ProjectFile;
import io.javafmt.daemon.protocol.FormatRequest;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

record FileScope(Optional<Path> path, JavafmtConfig.Analysis analysis) {

    FileScope {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(analysis, "analysis");
    }

    static FileScope none() {
        return new FileScope(Optional.empty(), new JavafmtConfig.Analysis.ParseOnly());
    }

    static FileScope of(final Project project, final VirtualFile file) {
        return ReadAction.computeBlocking(() -> new FileScope(
                Optional.ofNullable(file.getFileSystem().getNioPath(file)),
                Optional.ofNullable(ProjectFileIndex.getInstance(project).getModuleForFile(file))
                        .<JavafmtConfig.Analysis>map(module -> new JavafmtConfig.Analysis.Attributed(
                                classpath(module, TestSourcesFilter.isTestSources(file, project))))
                        .orElseGet(JavafmtConfig.Analysis.ParseOnly::new)));
    }

    JavafmtConfig config() {
        return JavafmtConfig.defaults().withAnalysis(analysis);
    }

    ProjectFile projectFile() {
        return path.map(ProjectFile::forFile).orElseGet(ProjectFile::none);
    }

    FormatRequest request(final int id, final String source) {
        final var request = FormatRequest.newBuilder()
                .setId(id)
                .setSource(source);
        path.ifPresent(file -> request.setPath(file.toString()));
        if (analysis instanceof JavafmtConfig.Analysis.Attributed(var classpath)) {
            request.setClasspath(classpath);
        }
        return request.build();
    }

    private static String classpath(final Module module, final boolean test) {
        final var compile = OrderEnumerator.orderEntries(module)
                .compileOnly()
                .withoutSdk()
                .recursively();
        return (test ? compile : compile.productionOnly()).classes()
                .usingCache()
                .getPathsList()
                .getPathsString();
    }
}
