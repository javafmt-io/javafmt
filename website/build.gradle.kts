import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node")
}

description = "Docusaurus website published to https://javafmt.io"

node {
    download.set(false)
}

val docusaurusBuild = tasks.register<NpmTask>("docusaurusBuild") {
    group = "build"
    description = "Build the Docusaurus website into website/build/."
    args.set(listOf("run", "build"))
    dependsOn(tasks.named("npmInstall"))
    inputs.files(fileTree(isolated.rootProject.projectDirectory.dir("docs")))
    inputs.files(fileTree(layout.projectDirectory.dir("src")))
    inputs.files(fileTree(layout.projectDirectory.dir("static")))
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(layout.projectDirectory.file("sidebars.ts"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    outputs.dir(layout.projectDirectory.dir("build"))
}

val docusaurusStart = tasks.register<NpmTask>("docusaurusStart") {
    group = "documentation"
    description = "Run the Docusaurus dev server (long-running)."
    args.set(listOf("run", "start"))
    dependsOn(tasks.named("npmInstall"))
}

val docusaurusServe = tasks.register<NpmTask>("docusaurusServe") {
    group = "documentation"
    description = "Serve the built site for local preview."
    args.set(listOf("run", "serve"))
    dependsOn(docusaurusBuild)
}

val docusaurusTypecheck = tasks.register<NpmTask>("docusaurusTypecheck") {
    group = "verification"
    description = "Run TypeScript type-checking on docusaurus.config.ts and sidebars.ts."
    args.set(listOf("run", "typecheck"))
    dependsOn(tasks.named("npmInstall"))
    inputs.file(layout.projectDirectory.file("docusaurus.config.ts"))
    inputs.file(layout.projectDirectory.file("sidebars.ts"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    outputs.file(layout.buildDirectory.file("typecheck.ok"))
    doLast { outputs.files.singleFile.writeText("ok") }
}

tasks.named("assemble") { dependsOn(docusaurusBuild) }
tasks.named("check") { dependsOn(docusaurusTypecheck) }
