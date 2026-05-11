import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.graalvm.buildtools.native")
    id("com.gradleup.shadow")
}

interface NativeBinary {
    val imageName: Property<String>
    val dockerLinuxName: Property<String>
    val dockerOutputDir: DirectoryProperty
}

val nativeBinary = extensions.create<NativeBinary>("nativeBinary")

nativeBinary.dockerLinuxName.convention(nativeBinary.imageName.map { "$it-linux-amd64" })
nativeBinary.dockerOutputDir.convention(layout.buildDirectory.dir("native/docker"))

graalvmNative {
    binaries {
        named("main") {
            imageName.set(nativeBinary.imageName)
            buildArgs.addAll(
                "--no-fallback",
                "-O2",
                "--initialize-at-build-time=org.slf4j",
                "--add-modules=jdk.compiler",
            )
        }
    }
    agent {
        defaultMode.set("standard")
    }
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar")

@Suppress("UNUSED_VARIABLE")
val nativeCompileLinuxDocker by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile Linux amd64 native binary using Docker (local cross-compilation)"
    dependsOn(shadowJarTask)

    val jarFile = shadowJarTask.flatMap { it.archiveFile }
    val outDir = nativeBinary.dockerOutputDir
    val binaryName = nativeBinary.dockerLinuxName

    inputs.file(jarFile)
    outputs.dir(outDir)

    executable = "docker"
    doFirst {
        outDir.get().asFile.mkdirs()
        args(
            "run", "--rm", "--platform", "linux/amd64",
            "-v", "${jarFile.get().asFile.absolutePath}:/input.jar:ro",
            "-v", "${outDir.get().asFile.absolutePath}:/output",
            "ghcr.io/graalvm/native-image-community:21",
            "-H:Name=${binaryName.get()}",
            "-H:Path=/output",
            "--no-fallback", "-O2",
            "--initialize-at-build-time=org.slf4j",
            "--add-modules=jdk.compiler",
            "-jar", "/input.jar",
        )
    }
}
