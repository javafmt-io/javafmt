import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    id("net.ltgt.errorprone")
}

// Apply javafmt's own Spotless wiring to every JVM module. `:javafmt-spotless`
// self-formats: its spotlessApply depends on its own jar, which depends on its
// own compileJava — a DAG, not a cycle. The jar built from unformatted source
// gets discarded next build once compileJava picks up the formatted output.
apply(plugin = "javafmt.spotless-conventions")

val libs = the<VersionCatalogsExtension>().named("libs")

group = "io.javafmt"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    constraints {
        implementation(libs.findLibrary("slf4j-api").get())
    }
    annotationProcessor(libs.findLibrary("lombok").get())
    compileOnly(libs.findLibrary("jspecify").get())
    compileOnly(libs.findLibrary("lombok").get())
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())
    testAnnotationProcessor(libs.findLibrary("lombok").get())
    testCompileOnly(libs.findLibrary("jspecify").get())
    testCompileOnly(libs.findLibrary("lombok").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
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

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("Werror", true)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.javafmt")
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        check("NullAway", CheckSeverity.OFF)
    }
}
