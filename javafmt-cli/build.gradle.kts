import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
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

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
    options.errorprone.option("NullAway:ExcludedFieldAnnotations", "picocli.CommandLine.Spec")
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

val nativeExecutable = configurations.consumable("nativeExecutable")

artifacts {
    add(nativeExecutable.name, tasks.named<BuildNativeImageTask>("nativeCompile").flatMap { it.outputDirectory })
}

val nativeDaemonTest = sourceSets.create("nativeDaemonTest")

configurations.named(nativeDaemonTest.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(nativeDaemonTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.named<JavaCompile>(nativeDaemonTest.compileJavaTaskName) {
    options.errorprone {
        check("NullAway", CheckSeverity.OFF)
    }
}

abstract class NativeBinaryDirectory : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val directory: DirectoryProperty

    override fun asArguments() = listOf("-Djavafmt.test.nativeBinaryDir=${directory.get().asFile.absolutePath}")
}

tasks.register<Test>("nativeDaemonTest") {
    description = "Runs the native javafmt binary as a daemon over both wire formats."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = nativeDaemonTest.output.classesDirs
    classpath = nativeDaemonTest.runtimeClasspath
    jvmArgumentProviders.add(objects.newInstance<NativeBinaryDirectory>().apply {
        directory = tasks.named<BuildNativeImageTask>("nativeCompile").flatMap { it.outputDirectory }
    })
    extensions.configure<JacocoTaskExtension> {
        isEnabled = false
    }
}

dependencies {
    implementation(project(":javafmt-core"))
    implementation(project(":javafmt-daemon"))
    implementation(libs.picocli)
    implementation(libs.slf4j.api)
    annotationProcessor(libs.picocli.codegen)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(testFixtures(project(":javafmt-core")))
    testImplementation(testFixtures(project(":javafmt-daemon")))
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
