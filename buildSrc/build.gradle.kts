plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    implementation("com.github.node-gradle:gradle-node-plugin:7.0.1")
}
