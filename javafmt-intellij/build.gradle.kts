import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    alias(libs.plugins.intellijPlatform)
}

group = "io.javafmt"
version = rootProject.version

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":javafmt-core"))
    compileOnly(libs.jspecify)

    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Bundled)
    }

    testImplementation(libs.junit4)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.opentest4j)
}

tasks.named<Test>("test") {
    jvmArgs("--add-modules=jdk.compiler")
}

intellijPlatform {
    pluginConfiguration {
        id = "io.javafmt.intellij"
        name = "javafmt"
        version = project.version.toString()
        description = "Opinionated, high-performance Java formatter for Java 21+."

        ideaVersion {
            sinceBuild = "242"
        }
    }
}
