plugins {
    id("javafmt.java-conventions")
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

tasks.named("build") { dependsOn(tasks.shadowJar) }
