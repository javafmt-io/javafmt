plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("versioning") {
            id = "javafmt.versioning"
            implementationClass = "VersioningPlugin"
        }
        register("reproducibleArchives") {
            id = "javafmt.reproducible-archives"
            implementationClass = "ReproducibleArchivesPlugin"
        }
        register("buildScans") {
            id = "javafmt.build-scans"
            implementationClass = "BuildScansPlugin"
        }
        register("toolchains") {
            id = "javafmt.toolchains"
            implementationClass = "ToolchainsPlugin"
        }
    }
}

dependencies {
    implementation(libs.develocity.gradle.plugin)
    implementation(libs.foojay.resolver)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
