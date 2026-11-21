import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "io.javafmt"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    annotationProcessor(libs.findLibrary("lombok").get())
    compileOnly(libs.findLibrary("jspecify").get())
    compileOnly(libs.findLibrary("lombok").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("lombok").get())
}

testing {
    suites.withType<JvmTestSuite>().configureEach {
        useJUnitJupiter(libs.findVersion("junit").get().requiredVersion)
        dependencies {
            compileOnly(libs.findLibrary("jspecify").get())
            implementation(libs.findLibrary("assertj-core").get())
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}
