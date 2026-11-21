import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    id("com.github.node-gradle.node")
}

node {
    // Use the Node/npm already on PATH; don't download a private copy.
    download.set(false)
}

tasks.withType<NpmTask>().matching { it.name != "npmInstall" }.configureEach {
    dependsOn(tasks.named("npmInstall"))
    inputs.files("package.json", "package-lock.json")
}
