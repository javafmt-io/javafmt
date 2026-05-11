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

dependencies {
    implementation(project(":javafmt-core"))
    implementation(libs.jackson.databind)
    runtimeOnly(libs.slf4j.simple)
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

fun nativeDaemonBinName(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isArm = arch.contains("aarch64") || arch.contains("arm")
    return when {
        os.contains("mac") && isArm -> "javafmt-daemon-darwin-arm64"
        os.contains("mac")          -> "javafmt-daemon-darwin-amd64"
        os.contains("linux")        -> "javafmt-daemon-linux-amd64"
        os.contains("win")          -> "javafmt-daemon-windows-amd64.exe"
        else                        -> "javafmt-daemon"
    }
}

val copyNativeBinary by tasks.registering(Copy::class) {
    dependsOn(tasks.named("nativeCompile"))
    from(layout.buildDirectory.dir("native/nativeCompile"))
    into(layout.projectDirectory.dir("bin"))
    include("javafmt-daemon", "javafmt-daemon.exe")
    rename { nativeDaemonBinName() }
}

tasks.named("packageVsix") {
    dependsOn(copyDaemonJar)
    inputs.dir(layout.projectDirectory.dir("bin"))
}
