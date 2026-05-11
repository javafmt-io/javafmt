plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.0")
    implementation("com.github.node-gradle:gradle-node-plugin:7.0.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.10.6")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.0.0")
}
