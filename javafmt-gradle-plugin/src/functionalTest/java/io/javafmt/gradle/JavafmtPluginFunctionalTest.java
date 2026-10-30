package io.javafmt.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import io.javafmt.Fixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavafmtPluginFunctionalTest {

    private static final String FOO = "src/main/java/Foo.java";

    private static final String FOO_TEST = "src/test/java/FooTest.java";

    private static final String GENERATED_FOO = "src/main/java/gen/Foo.java";

    private static final String A = "src/main/java/A.java";

    private static final String PROBLEMS_REPORT = "build/reports/problems/problems-report.html";

    private static final String STAR_IMPORT_SOURCE = "import java.util.*;\n\nclass Star {\n\n    List<String> xs;\n}\n";

    @Test
    void formatRewritesEveryJavaSourceSetInPlace(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        build.write(FOO_TEST, Fixtures.UNFORMATTED_A.replace("class A", "class FooTest"));
        final var result = build.run("javafmtFormat");
        assertThat(outcome(result, ":javafmtFormatMain")).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(outcome(result, ":javafmtFormatTest")).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(build.read(FOO)).isEqualTo(Fixtures.FORMATTED_FOO);
        assertThat(build.read(FOO_TEST)).isEqualTo(Fixtures.FORMATTED_A.replace("class A", "class FooTest"));
    }

    @Test
    void checkFailsOnUnformattedSourcesUntilTheyAreFormatted(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        assertThat(build.fail("check").getOutput()).contains("Foo.java").contains("javafmtFormat");
        assertThat(build.fail("check").getOutput()).contains("Foo.java");
        build.run("javafmtFormat");
        assertThat(outcome(build.run("check"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void checkReportsSourcesJavafmtCannotParse(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write("src/main/java/Broken.java", "class Broken {");
        assertThat(build.fail("javafmtCheck").getOutput()).containsPattern("Broken\\.java:\\d+:\\d+: error: ");
    }

    @Test
    void unformattedFilesErrorsAndWarningsAreReportedAsGradleProblems(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        build.write("src/main/java/Broken.java", "class Broken {");
        build.write("src/main/java/Star.java", STAR_IMPORT_SOURCE);
        build.fail("javafmtCheck");
        assertThat(build.read(PROBLEMS_REPORT))
                .contains("File not formatted by javafmt")
                .contains("Run 'gradle :javafmtFormat'")
                .contains("javafmt error")
                .contains("javafmt warning")
                .contains("Foo.java")
                .contains("Broken.java")
                .contains("Star.java");
    }

    @Test
    void unformattedFilesAndErrorsAreReportedAsErrorsAndWarningsAsWarnings(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        build.write("src/main/java/Broken.java", "class Broken {");
        build.write("src/main/java/Star.java", STAR_IMPORT_SOURCE);
        build.fail("javafmtCheck");
        assertThat(build.read(PROBLEMS_REPORT))
                .contains("\"displayName\":\"File not formatted by javafmt\"}],\"severity\":\"ERROR\"")
                .contains("\"displayName\":\"javafmt error\"}],\"severity\":\"ERROR\"")
                .contains("\"displayName\":\"javafmt warning\"}],\"severity\":\"WARNING\"");
    }

    @Test
    void jvmArgsReachTheWorkerThatFormats(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "javafmt { jvmArgs.add('-XX:+JavafmtNoSuchFlag') }");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        assertThat(build.fail("javafmtCheck").getOutput()).contains("JavafmtNoSuchFlag");
    }

    @Test
    void theFailureListsEachUnformattedFileOnceAlongsideItsLocation(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        final var output = build.fail("javafmtCheck").getOutput();
        final var wentWrong = output.substring(output.lastIndexOf("* What went wrong:"), output.lastIndexOf("* Try:"));
        assertThat(wentWrong.lines().map(String::strip).filter(line -> line.contains("Foo.java")).toList())
                .hasSize(2)
                .first()
                .isEqualTo(FOO);
    }

    @Test
    void theOldestSupportedGradleReportsUnformattedFiles(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        assertThat(build.failOn(TestBuild.MINIMUM_GRADLE_VERSION, "javafmtCheck").getOutput()).contains("Foo.java").contains("javafmtFormat");
        assertThat(build.read(PROBLEMS_REPORT)).contains("File not formatted by javafmt");
    }

    @Test
    void checkPrintsWarningsWithoutFailing(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write("src/main/java/Star.java", STAR_IMPORT_SOURCE);
        assertThat(build.run("javafmtCheck").getOutput()).containsPattern("Star\\.java:\\d+:\\d+: warning: ");
    }

    @Test
    void checkIsUpToDateWhenNothingChanged(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        build.run("javafmtCheck");
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.UP_TO_DATE);
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        assertThat(build.fail("javafmtCheck").getOutput()).contains("Foo.java");
    }

    @Test
    void checkResultsComeFromTheBuildCache(@TempDir final Path dir, @TempDir final Path cache) {
        final var build = TestBuild.singleProject(dir, "");
        build.write("settings.gradle", "rootProject.name = 'demo'\nbuildCache { local { directory = '" + cache.toUri() + "' } }\n");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        assertThat(outcome(build.run("javafmtCheck", "--build-cache"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
        build.run("clean");
        assertThat(outcome(build.run("javafmtCheck", "--build-cache"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.FROM_CACHE);
    }

    @Test
    void theConfigurationCacheIsReused(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        assertThat(build.run("javafmtFormat", "javafmtCheck").getOutput()).contains("Configuration cache entry stored");
        assertThat(build.run("javafmtFormat", "javafmtCheck").getOutput()).contains("Reusing configuration cache");
    }

    @ParameterizedTest
    @ValueSource(strings = {"**/Foo.java", "src/main/java/gen/**"})
    void excludedSourcesAreLeftAlone(final String pattern, @TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "javafmt {\n    excludes = ['" + pattern + "']\n}\n");
        build.write(GENERATED_FOO, Fixtures.UNFORMATTED_FOO);
        build.write(FOO_TEST, Fixtures.FORMATTED_A.replace("class A", "class FooTest"));
        build.run("javafmtFormat", "javafmtCheck");
        assertThat(build.read(GENERATED_FOO)).isEqualTo(Fixtures.UNFORMATTED_FOO);
    }

    @Test
    void sourceDirectoriesUnderTheBuildDirectoryAreSkipped(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "sourceSets.main.java.srcDir('build/generated/sources/demo')\n");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        build.write("build/generated/sources/demo/Generated.java", Fixtures.UNFORMATTED_A);
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void anIncrementalCheckOnlyReadsTheChangedFiles(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        build.write(A, Fixtures.FORMATTED_A);
        assertThat(build.run("javafmtCheck", "--info").getOutput()).contains("javafmt: processed 2 file(s)");
        build.write(A, Fixtures.FORMATTED_A.replace("int x;", "int y;"));
        assertThat(build.run("javafmtCheck", "--info").getOutput()).contains("javafmt: processed 1 file(s)");
    }

    @Test
    void excludesMatchSourcesOutsideTheProjectByTheirPathFromTheProjectDirectory(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir.resolve("app"), """
                sourceSets.main.java.srcDir('../shared')
                javafmt {
                    excludes = ['../shared/gen/**']
                }
                """);
        build.write("../shared/gen/Generated.java", Fixtures.UNFORMATTED_A);
        build.write("../shared/Shared.java", Fixtures.FORMATTED_FOO);
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
        build.write("../shared/Shared.java", Fixtures.UNFORMATTED_FOO);
        assertThat(build.fail("javafmtCheck").getOutput()).contains("Shared.java").doesNotContain("Generated.java");
    }

    @Test
    void everyJavafmtTaskSharesOneWorkerSlot(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, """
                def slot = gradle.sharedServices.registrations.findByName('javafmtWorkerSlot')
                println "javafmt worker slots: ${slot.maxParallelUsages.get()}"
                ['javafmtCheckMain', 'javafmtCheckTest', 'javafmtFormatMain', 'javafmtFormatTest'].each { name ->
                    tasks.named(name) { task ->
                        println "${name} uses the slot: ${task.workerSlot.get().is(slot.service.get())}"
                    }
                }
                """);
        build.write(FOO, Fixtures.FORMATTED_FOO);
        assertThat(build.run("javafmtCheck", "javafmtFormat", "--parallel").getOutput())
                .contains("javafmt worker slots: 1")
                .contains("javafmtCheckMain uses the slot: true")
                .contains("javafmtCheckTest uses the slot: true")
                .contains("javafmtFormatMain uses the slot: true")
                .contains("javafmtFormatTest uses the slot: true");
    }

    @Test
    void renamedAndDeletedSourcesDoNotFailTheCheck(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.write(FOO, Fixtures.FORMATTED_FOO);
        build.write(A, Fixtures.FORMATTED_A);
        build.run("javafmtCheck");
        build.move(A, "src/main/java/B.java");
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
        build.delete("src/main/java/B.java");
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void aMissingJava25ToolchainFailsWithTheFix(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "");
        build.gradleProperties(List.of("org.gradle.java.home=" + TestBuild.OLDER_JAVA_HOME, "org.gradle.java.installations.paths="));
        build.write(FOO, Fixtures.FORMATTED_FOO);
        assertThat(build.fail("javafmtCheck").getOutput())
                .contains("javafmt needs a Java 25 toolchain")
                .containsPattern("id\\(\"org\\.gradle\\.toolchains\\.foojay-resolver-convention\"\\) version \"\\d+\\.\\d+\\.\\d+\"");
    }

    @Test
    void analyzeResolvesTypesFromTheCompileClasspath(@TempDir final Path dir) {
        final var build = new TestBuild(dir);
        build.gradleProperties(List.of());
        build.write("settings.gradle", "rootProject.name = 'demo'\ninclude 'lib', 'app'\n");
        build.write("lib/build.gradle", "plugins {\n    id 'java-library'\n}\n");
        build.write("lib/src/main/java/com/example/lib/Greeter.java", """
                package com.example.lib;

                public class Greeter {

                    public String greet() {
                        return "hi";
                    }
                }
                """);
        build.write("app/build.gradle", """
                plugins {
                    id 'java'
                    id 'io.javafmt'
                }

                dependencies {
                    implementation project(':lib')
                }

                javafmt {
                    analyze = true
                }
                """);
        build.write("app/src/main/java/com/example/app/App.java", """
                package com.example.app;

                import com.example.lib.*;

                public class App {

                    public String run() {
                        return new Greeter().greet();
                    }
                }
                """);
        build.run(":app:javafmtFormat");
        assertThat(build.read("app/src/main/java/com/example/app/App.java"))
                .contains("import com.example.lib.Greeter;")
                .doesNotContain("com.example.lib.*");
    }

    @Test
    void javafmtStaysOffTheBuildScriptClasspath(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, """
                def visible = true
                try {
                    Class.forName('io.javafmt.Javafmt')
                } catch (ClassNotFoundException ignored) {
                    visible = false
                }
                println "javafmt visible: $visible"
                """);
        assertThat(build.run("help").getOutput()).contains("javafmt visible: false");
    }

    @Test
    void anOlderBuildJvmFormatsOnAJava25Toolchain(@TempDir final Path dir) {
        final var build = TestBuild.singleProject(dir, "println \"build JVM: ${Runtime.version().feature()}\"\n");
        build.gradleProperties(List.of("org.gradle.java.home=" + TestBuild.OLDER_JAVA_HOME));
        build.write(FOO, Fixtures.UNFORMATTED_FOO);
        assertThat(build.run("javafmtFormat").getOutput()).contains("build JVM: 21");
        assertThat(build.read(FOO)).isEqualTo(Fixtures.FORMATTED_FOO);
        assertThat(outcome(build.run("javafmtCheck"), ":javafmtCheckMain")).isEqualTo(TaskOutcome.SUCCESS);
    }

    private static TaskOutcome outcome(final BuildResult result, final String path) {
        return Objects.requireNonNull(result.task(path), path).getOutcome();
    }
}
