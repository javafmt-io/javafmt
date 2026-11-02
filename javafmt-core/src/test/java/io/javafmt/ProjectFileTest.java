package io.javafmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFileTest {
    private static final String LOCATION = "/repository/.javafmt";

    @Test
    void analyzeSkipListsTheRulesToRunWithoutTheClasspath() {
        assertThat(ProjectFile.parse(LOCATION, "analyze.skip=ExplodeStarImports, LocalVarUseVar\n"))
                .isEqualTo(new ProjectFile.Settings(LOCATION, Set.of("ExplodeStarImports", "LocalVarUseVar")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# nothing to skip\n", "analyze.skip=\n", "analyze.skip = , \n"})
    void aFileThatSkipsNothingIsValid(final String text) {
        assertThat(ProjectFile.parse(LOCATION, text)).isEqualTo(new ProjectFile.Settings(LOCATION, Set.of()));
    }

    @Test
    void anUnknownRuleIsAnErrorThatNamesItAndTheRulesThatUseTheClasspath() {
        assertThat(ProjectFile.parse(LOCATION, "analyze.skip=ExplodeStarImport\n"))
                .isInstanceOfSatisfying(
                        ProjectFile.Invalid.class,
                        invalid -> assertThat(invalid.problem())
                                .contains(LOCATION, "ExplodeStarImport", "ExplodeStarImports, LocalVarUseVar, RedundantTypeArguments"));
    }

    @Test
    void aRuleThatDoesNotUseTheClasspathIsAnError() {
        assertThat(ProjectFile.parse(LOCATION, "analyze.skip=FinalParameters\n"))
                .isInstanceOfSatisfying(
                        ProjectFile.Invalid.class,
                        invalid -> assertThat(invalid.problem()).contains("FinalParameters", "does not use the classpath"));
    }

    @Test
    void anUnknownSettingIsAnError() {
        assertThat(ProjectFile.parse(LOCATION, "analyse.skip=LocalVarUseVar\n"))
                .isInstanceOfSatisfying(ProjectFile.Invalid.class, invalid -> assertThat(invalid.problem()).contains("analyse.skip", "analyze.skip"));
    }

    @Test
    void theNearestProjectFileAboveTheSourceApplies(@TempDir final Path dir) throws IOException {
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        Files.writeString(Files.createDirectories(dir.resolve("module")).resolve(".javafmt"), "analyze.skip=ExplodeStarImports\n");
        final var source = Files.createDirectories(dir.resolve("module/src/main/java")).resolve("Order.java");
        assertThat(ProjectFile.forFile(source))
                .isEqualTo(new ProjectFile.Settings(dir.resolve("module/.javafmt").toString(), Set.of("ExplodeStarImports")));
    }

    @Test
    void theSearchStopsAtTheRepositoryRoot(@TempDir final Path dir) throws IOException {
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        final var repository = Files.createDirectories(dir.resolve("repository"));
        Files.createDirectories(repository.resolve(".git"));
        final var source = Files.createDirectories(repository.resolve("src")).resolve("Order.java");
        assertThat(ProjectFile.forFile(source)).isEqualTo(new ProjectFile.None());
    }

    @Test
    void aProjectFileBesideTheGitDirectoryApplies(@TempDir final Path dir) throws IOException {
        Files.writeString(dir.resolve(".git"), "gitdir: ../elsewhere\n");
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=RedundantTypeArguments\n");
        final var source = Files.createDirectories(dir.resolve("src")).resolve("Order.java");
        assertThat(ProjectFile.forFile(source)).isEqualTo(new ProjectFile.Settings(dir.resolve(".javafmt").toString(), Set.of("RedundantTypeArguments")));
    }

    @Test
    void aDirectoryIsSearchedFromItself(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        assertThat(ProjectFile.forDirectory(dir)).isEqualTo(new ProjectFile.Settings(dir.resolve(".javafmt").toString(), Set.of("LocalVarUseVar")));
    }

    @Test
    void manyFilesShareTheProjectFilesThatGovernThem(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".javafmt"), "analyze.skip=LocalVarUseVar\n");
        final var module = Files.createDirectories(dir.resolve("module"));
        Files.writeString(module.resolve(".javafmt"), "analyze.skip=ExplodeStarImports\n");
        final var files = List.of(dir.resolve("Order.java"), dir.resolve("Invoice.java"), module.resolve("Customer.java"));
        assertThat(ProjectFile.forFiles(files))
                .containsExactlyInAnyOrder(
                        new ProjectFile.Settings(dir.resolve(".javafmt").toString(), Set.of("LocalVarUseVar")),
                        new ProjectFile.Settings(module.resolve(".javafmt").toString(), Set.of("ExplodeStarImports")));
    }

    @Test
    void theSkippedRulesKeepOneOrderSoTheSerializedFormIsStable() {
        final var settings = (ProjectFile.Settings) ProjectFile.parse(LOCATION, "analyze.skip=RedundantTypeArguments, ExplodeStarImports, LocalVarUseVar\n");
        assertThat(settings.analyzeSkip()).containsExactly("ExplodeStarImports", "LocalVarUseVar", "RedundantTypeArguments");
    }

    @Test
    void noneSkipsNothing() {
        assertThat(ProjectFile.none().analyzeSkip()).isEmpty();
    }
}
