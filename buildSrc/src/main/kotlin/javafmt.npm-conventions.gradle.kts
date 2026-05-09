import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node")
}

node {
    // Use the Node/npm already on PATH; don't download a private copy.
    download.set(false)
}

val npmBuild by tasks.registering(NpmTask::class) {
    args.set(listOf("run", "compile"))
    dependsOn(tasks.named("npmInstall"))
    inputs.files(fileTree(layout.projectDirectory.dir("src")) {
        include("**/*.ts")
        exclude("test/unit/**", "__mocks__/**")
    })
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.dir(layout.projectDirectory.dir("out"))
}

val npmTest by tasks.registering(NpmTask::class) {
    args.set(listOf("test"))
    dependsOn(tasks.named("npmInstall"))
    inputs.files(fileTree(layout.projectDirectory.dir("src")) { include("**/*.ts") })
    inputs.file(layout.projectDirectory.file("jest.config.js"))
    outputs.file(layout.buildDirectory.file("test-results.json"))
}

val npmTestIntegration by tasks.registering(NpmTask::class) {
    group = "verification"
    description = "Runs VS Code integration tests inside the Extension Development Host; skipped when 'code' is not on PATH."
    args.set(listOf("run", "test:integration"))
    dependsOn(npmBuild, tasks.named("npmInstall"))
    // Integration tests have no stable file output, so Gradle always considers them out-of-date
    // when they run — that's correct for tests with external dependencies (a live VS Code instance).
}

// packageVsix runs `npm run package` which invokes vsce via the script defined in package.json.
// Projects that bundle a daemon jar should wire: tasks.named("packageVsix") { dependsOn(copyDaemonJar) }
val packageVsix by tasks.registering(NpmTask::class) {
    args.set(listOf("run", "package"))
    dependsOn(npmBuild)
    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.file(layout.projectDirectory.file(".vscodeignore"))
    inputs.dir(layout.projectDirectory.dir("out"))
    outputs.file(layout.buildDirectory.file("javafmt-vscode.vsix"))
}

tasks.named("check") { dependsOn(npmTest, npmTestIntegration) }
tasks.named("build") { dependsOn(packageVsix) }
