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
    }
}

dependencies {
    implementation(libs.develocity.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
