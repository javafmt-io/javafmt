buildscript {
    dependencies {
        constraints {
            classpath(libs.asm)
        }
    }
}

plugins {
    id("javafmt.java-conventions")
    id("javafmt.publish-conventions")
    alias(libs.plugins.maven.plugin.development)
}

description = "Maven plugin for javafmt, the opinionated Java code formatter."

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(testFixtures(project(":javafmt-core")))
    testCompileOnly(libs.maven.plugin.annotations)
    testRuntimeOnly(libs.slf4j.simple)
}

publishing {
    publications.named<MavenPublication>("mavenJava") {
        pom.packaging = "maven-plugin"
    }
}
