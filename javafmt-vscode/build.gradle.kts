plugins {
    id("javafmt.java-conventions")
    id("javafmt.npm-conventions")
    application
    alias(libs.plugins.shadow)
}

application {
    mainClass.set("io.javafmt.vscode.Daemon")
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

tasks.named("packageVsix") {
    dependsOn(copyDaemonJar)
    inputs.dir(layout.projectDirectory.dir("bin"))
}
