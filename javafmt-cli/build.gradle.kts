import java.io.PrintStream

plugins {
    id("javafmt.java-conventions")
    application
    id("javafmt.native-conventions")
}

application {
    mainClass.set("io.javafmt.cli.Main")
}

nativeBinary {
    imageName.set("javafmt")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("io.javafmt.cli.Main")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
        }
    }
}

dependencies {
    implementation(project(":javafmt-core"))
    runtimeOnly(libs.slf4j.simple)
    testImplementation(testFixtures(project(":javafmt-core")))
}

// Tracing-agent capture: runs the CLI against javafmt-core's test fixture corpus so the
// GraalVM native-image-agent (attached automatically when -Pagent is passed) observes the
// full parse -> format -> lint path. The resulting reachability metadata can then be merged
// into src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli/ via:
//
//   gradle :javafmt-cli:tracingAgentCapture -Pagent
//   gradle :javafmt-cli:metadataCopy \
//       --task tracingAgentCapture \
//       --dir javafmt-cli/src/main/resources/META-INF/native-image/io.javafmt/javafmt-cli
//
// Requires a GraalVM JDK on the toolchain path (Temurin/OpenJDK ship no native-image-agent
// dylib). Without -Pagent the task is a plain CLI exercise and is harmless.
val tracingAgentCapture = tasks.register<JavaExec>("tracingAgentCapture") {
    group = "native"
    description = "Run the CLI against the fixture corpus with --check so the native-image-agent can record reflection and resource hints."
    mainClass.set(application.mainClass)
    classpath = sourceSets.main.get().runtimeClasspath
    // The native-image-agent dylib only ships with GraalVM, not Temurin/OpenJDK.
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    })
    // --check exits 1 when input != expected (most non-idempotent fixtures); we only care
    // about exercising parse/format code paths under the agent, not the diagnostic outcome.
    isIgnoreExitValue = true

    val fixturesRoot = isolated.rootProject.projectDirectory
        .dir("javafmt-core/src/test/resources/test-fixtures")
    inputs.dir(fixturesRoot).withPropertyName("fixtures").withPathSensitivity(PathSensitivity.RELATIVE)

    val logFile = layout.buildDirectory.file("tracing-agent/capture.log")
    outputs.file(logFile)

    doFirst {
        val fixtureFiles = fileTree(fixturesRoot) {
            include("**/*.java")
        }.files.map { it.absolutePath }.sorted()
        args("--check")
        args(fixtureFiles)
        logger.lifecycle("tracingAgentCapture: exercising ${fixtureFiles.size} fixture files")
        val out = logFile.get().asFile
        out.parentFile.mkdirs()
        val stream = PrintStream(out.outputStream(), true, Charsets.UTF_8)
        standardOutput = stream
        errorOutput = stream
    }
}
