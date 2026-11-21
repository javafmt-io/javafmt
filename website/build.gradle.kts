import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("javafmt.npm-conventions")
}

description = "Docusaurus website published to https://javafmt.io"

val siteConfig = files(
    "docusaurus.config.ts",
    "tsconfig.json",
)

val docusaurusBuild = tasks.register<NpmTask>("docusaurusBuild") {
    group = "build"
    description = "Build the Docusaurus website into website/build/site/."
    args.set(listOf("run", "build"))
    inputs.dir(isolated.rootProject.projectDirectory.dir("docs"))
    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.files(siteConfig)
    outputs.dir(layout.buildDirectory.dir("site"))
}

tasks.register<NpmTask>("docusaurusStart") {
    group = "documentation"
    description = "Run the Docusaurus dev server (long-running)."
    args.set(listOf("run", "start"))
}

tasks.register<NpmTask>("docusaurusServe") {
    group = "documentation"
    description = "Serve the built site for local preview."
    args.set(listOf("run", "serve"))
    dependsOn(docusaurusBuild)
}

val docusaurusTypecheck = tasks.register<NpmTask>("docusaurusTypecheck") {
    group = "verification"
    description = "Run TypeScript type-checking on docusaurus.config.ts."
    args.set(listOf("run", "typecheck"))
    inputs.files(siteConfig)
    outputs.file(layout.buildDirectory.file("typecheck.ok"))
    doLast { outputs.files.singleFile.writeText("ok") }
}

tasks.named("assemble") { dependsOn(docusaurusBuild) }
tasks.named("check") { dependsOn(docusaurusTypecheck) }
