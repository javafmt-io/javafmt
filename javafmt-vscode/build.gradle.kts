import com.github.gradle.node.npm.task.NpmTask
import java.io.PrintStream

plugins {
    id("javafmt.java-conventions")
    id("javafmt.npm-conventions")
    application
    id("javafmt.native-conventions")
}

application {
    mainClass.set("io.javafmt.vscode.Daemon")
}

nativeBinary {
    imageName.set("javafmt-daemon")
    dockerOutputDir.set(layout.projectDirectory.dir("bin"))
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("io.javafmt.vscode.Daemon")
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
        }
    }
}

dependencies {
    implementation(project(":javafmt-core"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
}

// Tracing-agent capture: feeds real NDJSON requests through the daemon's actual Daemon.main()
// entry point so the GraalVM native-image-agent observes the exact Jackson code paths the native
// binary must survive on its first real request -- deserializing FormatRequest, then serializing
// a FormatResponse with both an empty diagnostics list and one holding a real DiagnosticDto. The
// unit tests construct several of these records directly without going through Json; this task is
// the one place that drives the full Daemon.serve loop end to end under the agent. Modelled on
// javafmt-cli's tracingAgentCapture task.
//
//   gradle :javafmt-vscode:daemonAgentCapture -Pagent --no-configuration-cache
//   gradle :javafmt-vscode:metadataCopy --no-configuration-cache \
//       --task daemonAgentCapture \
//       --dir src/main/resources/META-INF/native-image/io.javafmt/javafmt-vscode
//
// Note --dir is resolved relative to this module's project directory, not the repo root.
//
// Requires a GraalVM JDK on the toolchain path (Temurin/OpenJDK ship no native-image-agent
// dylib) -- set JAVA_HOME/GRAALVM_HOME to it, since metadataCopy locates native-image-configure
// via the environment rather than the task's own javaLauncher. Without -Pagent the task is a
// harmless daemon exercise.
val daemonAgentCapture by tasks.registering(JavaExec::class) {
    group = "native"
    description = "Feed real NDJSON requests through Daemon.main() so the native-image-agent can record the Jackson reflection the daemon needs."
    mainClass.set("io.javafmt.vscode.Daemon")
    classpath = sourceSets.main.get().runtimeClasspath
    // The native-image-agent dylib only ships with GraalVM, not Temurin/OpenJDK.
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.GRAAL_VM)
    })

    val ndjson = listOf(
        // Clean input: no diagnostics -- exercises FormatResponse serialized with an empty list.
        """{"id":"1","source":"class A{}"}""",
        // A FallThrough lint warning: exercises FormatResponse serialized with a real DiagnosticDto.
        """{"id":"2","source":"class Foo { void m(int x) { switch (x) { case 1: System.out.println(); case 2: break; } } }"}""",
    ).joinToString(separator = "\n", postfix = "\n")
    standardInput = ndjson.byteInputStream(Charsets.UTF_8)

    val logFile = layout.buildDirectory.file("tracing-agent/daemon-capture.log")
    outputs.file(logFile)

    doFirst {
        val out = logFile.get().asFile
        out.parentFile.mkdirs()
        val stream = PrintStream(out.outputStream(), true, Charsets.UTF_8)
        standardOutput = stream
        errorOutput = stream
    }
}

tasks.shadowJar {
    archiveBaseName.set("javafmt-daemon")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

val copyDaemonJar by tasks.registering(Copy::class) {
    from(tasks.shadowJar)
    into(layout.projectDirectory.dir("bin"))
    rename { "javafmt-daemon.jar" }
}

// The full vsce target vocabulary this scheme lays bin/ out for, regardless of which of them
// this repo's CI actually has runners to build (see native.yml's matrix for the current three).
val vsceTargets = listOf("darwin-arm64", "darwin-x64", "linux-arm64", "linux-x64", "win32-x64")

fun vsceTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isArm = arch.contains("aarch64") || arch.contains("arm")
    return when {
        os.contains("mac") && isArm -> "darwin-arm64"
        os.contains("mac")          -> "darwin-x64"
        os.contains("linux") && isArm -> "linux-arm64"
        os.contains("linux")        -> "linux-x64"
        os.contains("win")          -> "win32-x64"
        else -> throw GradleException("unsupported platform for vsce target: $os/$arch")
    }
}

// The target being packaged for, when that differs from the machine vsceTarget() autodetects --
// e.g. a future publish job that stages every matrix leg's artifact into one bin/ before packaging
// each VSIX in turn. VSCE_TARGET matches the env var native.yml/package:target already key off.
fun requestedTarget(): String =
    System.getenv("VSCE_TARGET")
        ?: (findProperty("vsceTarget") as String?)
        ?: vsceTarget()

val copyNativeBinary by tasks.registering(Copy::class) {
    dependsOn(tasks.named("nativeCompile"))
    from(layout.buildDirectory.dir("native/nativeCompile"))
    into(layout.projectDirectory.dir("bin").dir(vsceTarget()))
    include("javafmt-daemon", "javafmt-daemon.exe")
}

// vsce 3.9.1 documents --target as packaging a single platform and --ignore-other-target-folders
// as excluding sibling target directories, but neither actually filters files: --target only tags
// VSIX manifest metadata (and gates node_modules dependency detection), and
// ignoreOtherTargetFolders is parsed by vsce's CLI (main.js) and never read by its file-collection
// logic (package.js) -- dead code in this version. So bin/ is staged explicitly here rather than
// trusted to vsce: this task deletes every target directory except the one being packaged, plus
// the universal jar, before vsce ever runs.
val stageTargetBinary by tasks.registering(Delete::class) {
    dependsOn(copyNativeBinary)
    val target = requestedTarget()
    delete(vsceTargets.filter { it != target }.map { layout.projectDirectory.dir("bin").dir(it) })
    delete(layout.projectDirectory.file("bin/javafmt-daemon.jar"))
}

val packageVsixTarget by tasks.registering(NpmTask::class) {
    dependsOn(tasks.named("npmBuild"), stageTargetBinary)
    args.set(listOf("run", "package:target", "--", requestedTarget()))
    inputs.dir(layout.projectDirectory.dir("bin"))
}

tasks.named("packageVsix") {
    dependsOn(copyDaemonJar)
    inputs.dir(layout.projectDirectory.dir("bin"))
}
