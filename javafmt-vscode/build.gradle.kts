import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("javafmt.npm-conventions")
}

val nativeExecutable = configurations.dependencyScope("nativeExecutable")

val nativeExecutableFiles = configurations.resolvable("nativeExecutableFiles") {
    extendsFrom(nativeExecutable.get())
}

val daemonProto = configurations.dependencyScope("daemonProto")

val daemonProtoFiles = configurations.resolvable("daemonProtoFiles") {
    extendsFrom(daemonProto.get())
}

val daemonFixtures = configurations.dependencyScope("daemonFixtures")

val daemonFixtureFiles = configurations.resolvable("daemonFixtureFiles") {
    extendsFrom(daemonFixtures.get())
}

dependencies {
    nativeExecutable(project(path = ":javafmt-cli", configuration = "nativeExecutable"))
    daemonProto(project(path = ":javafmt-daemon", configuration = "protoElements"))
    daemonFixtures(project(path = ":javafmt-daemon", configuration = "fixtureElements"))
}

val syncDaemonProto = tasks.register<Sync>("syncDaemonProto") {
    from(daemonProtoFiles)
    into(layout.buildDirectory.dir("daemon-proto"))
}

val syncDaemonFixtures = tasks.register<Sync>("syncDaemonFixtures") {
    from(daemonFixtureFiles)
    into(layout.buildDirectory.dir("daemon-fixtures"))
}

val generateProtocol = tasks.register<NpmTask>("generateProtocol") {
    args.set(listOf("run", "generate"))
    dependsOn(tasks.named("npmInstall"))
    inputs.files(syncDaemonProto).withPropertyName("proto")
    inputs.file(layout.projectDirectory.file("buf.gen.yaml"))
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    outputs.dir(layout.projectDirectory.dir("src/gen"))
}

tasks.named("npmBuild") {
    dependsOn(generateProtocol)
}

tasks.named("npmTest") {
    dependsOn(generateProtocol)
    inputs.files(syncDaemonFixtures).withPropertyName("fixtures")
}

// The full vsce target vocabulary this scheme lays bin/ out for, regardless of which of them
// this repo's CI actually has runners to build (see native.yml's matrix for the ones it does).
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

val copyNativeBinary = tasks.register<Copy>("copyNativeBinary") {
    from(nativeExecutableFiles)
    into(layout.projectDirectory.dir("bin").dir(vsceTarget()))
    include("javafmt", "javafmt.exe")
}

// vsce 3.9.1 documents --target as packaging a single platform and --ignore-other-target-folders
// as excluding sibling target directories, but neither actually filters files: --target only tags
// VSIX manifest metadata (and gates node_modules dependency detection), and
// ignoreOtherTargetFolders is parsed by vsce's CLI (main.js) and never read by its file-collection
// logic (package.js) -- dead code in this version. So bin/ is staged explicitly here rather than
// trusted to vsce: this task deletes everything in bin/ except the target directory being packaged
// before vsce ever runs.
val stageTargetBinary = tasks.register<Delete>("stageTargetBinary") {
    dependsOn(copyNativeBinary)
    val target = requestedTarget()
    delete(vsceTargets.filter { it != target }.map { layout.projectDirectory.dir("bin").dir(it) })
    delete(fileTree(layout.projectDirectory.dir("bin")) { exclude("$target/**") })
}

tasks.register<NpmTask>("packageVsixTarget") {
    dependsOn(tasks.named("npmBuild"), stageTargetBinary)
    val vsixVersion = project.version.toString().substringBefore('-')
    inputs.property("vsixVersion", vsixVersion)
    args.set(listOf("run", "package:target", "--", requestedTarget(), vsixVersion, "--no-git-tag-version", "--no-update-package-json"))
    inputs.dir(layout.projectDirectory.dir("bin"))
}
