import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node")
}

description = "Docusaurus website published to https://javafmt.io"

node {
    download.set(false)
}

val docusaurusBuild by tasks.registering(NpmTask::class) {
    group = "build"
    description = "Build the Docusaurus website into website/build/."
    args.set(listOf("run", "build"))
    dependsOn(tasks.named("npmInstall"))
    inputs.files(fileTree(rootProject.layout.projectDirectory.dir("docs")))
    inputs.files(fileTree(layout.projectDirectory.dir("src")))
    inputs.files(fileTree(layout.projectDirectory.dir("static")))
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(layout.projectDirectory.file("sidebars.ts"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    inputs.file(layout.projectDirectory.file("package.json"))
    outputs.dir(layout.projectDirectory.dir("build"))
}

val docusaurusStart by tasks.registering(NpmTask::class) {
    group = "documentation"
    description = "Run the Docusaurus dev server (long-running)."
    args.set(listOf("run", "start"))
    dependsOn(tasks.named("npmInstall"))
}

val docusaurusServe by tasks.registering(NpmTask::class) {
    group = "documentation"
    description = "Serve the built site for local preview."
    args.set(listOf("run", "serve"))
    dependsOn(docusaurusBuild)
}

val docusaurusTypecheck by tasks.registering(NpmTask::class) {
    group = "verification"
    description = "Run TypeScript type-checking on docusaurus.config.ts and sidebars.ts."
    args.set(listOf("run", "typecheck"))
    dependsOn(tasks.named("npmInstall"))
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(layout.projectDirectory.file("sidebars.ts"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.file(layout.buildDirectory.file("typecheck.ok"))
    doLast { outputs.files.singleFile.writeText("ok") }
}

tasks.named("assemble") { dependsOn(docusaurusBuild) }
tasks.named("check") { dependsOn(docusaurusTypecheck) }
