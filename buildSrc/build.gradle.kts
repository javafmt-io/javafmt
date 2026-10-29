plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.node.gradle.plugin)
    implementation(libs.graalvm.native.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
