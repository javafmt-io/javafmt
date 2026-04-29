plugins {
    id("javafmt.java-conventions")
    alias(libs.plugins.maven.plugin.development)
}

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testCompileOnly(libs.maven.plugin.annotations)
    testRuntimeOnly(libs.slf4j.simple)
}
