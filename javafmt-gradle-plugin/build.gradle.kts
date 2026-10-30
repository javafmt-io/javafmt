import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    id("javafmt.java-conventions")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
}

description = "Gradle plugin for javafmt, the opinionated Java code formatter."

tasks.named<JavaCompile>("compileJava") {
    options.release = 17
    options.errorprone.disable("JavaxInjectOnAbstractMethod")
}

configurations.implementation {
    dependencyConstraints.clear()
}

listOf(configurations.apiElements, configurations.runtimeElements).forEach { elements ->
    elements.configure {
        attributes {
            attribute(
                GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                objects.named(libs.versions.gradlePluginMinimumGradle.get()),
            )
        }
    }
}

val engine = sourceSets.create("engine")
val functionalTest = sourceSets.create("functionalTest")

dependencies {
    "engineImplementation"(project(":javafmt-core"))
    "engineCompileOnly"(libs.jspecify)
    compileOnly(engine.output)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(platform(libs.junit.bom))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(libs.assertj.core)
    "functionalTestImplementation"(testFixtures(project(":javafmt-core")))
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)
}

val engineRuntimeClasspath = configurations.named(engine.runtimeClasspathConfigurationName)

interface ArchiveServices {
    @get:Inject
    val archives: ArchiveOperations
}

val engineJar = tasks.register<Jar>("engineJar") {
    val archives = objects.newInstance<ArchiveServices>().archives
    archiveFileName = "javafmt-engine.jar"
    destinationDirectory = layout.buildDirectory.dir("engine")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(engine.output)
    from(engineRuntimeClasspath.flatMap { it.elements }.map { jars -> jars.map { archives.zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
}

val pluginProperties = tasks.register<WriteProperties>("pluginProperties") {
    val engineFile = engineJar.flatMap { it.archiveFile }
    inputs.file(engineFile)
    destinationFile = layout.buildDirectory.file("plugin-properties/javafmt-gradle-plugin.properties")
    property("engine", engineFile.map { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it.asFile.readBytes())) })
    property("foojay", libs.versions.foojay.get())
}

val pluginResources = tasks.register<Sync>("pluginResources") {
    into(layout.buildDirectory.dir("generated/plugin-resources"))
    into("io/javafmt/gradle") {
        from(engineJar)
        from(pluginProperties)
    }
}

sourceSets.main {
    resources.srcDir(pluginResources)
}

tasks.withType<JavaCompile>().configureEach {
    if (name == "compileFunctionalTestJava") {
        options.errorprone {
            check("NullAway", CheckSeverity.OFF)
        }
    }
}

abstract class JavaHomes : CommandLineArgumentProvider {
    @get:Nested
    abstract val java25: Property<JavaLauncher>

    @get:Nested
    abstract val older: Property<JavaLauncher>

    override fun asArguments() = listOf(
        "-Djavafmt.test.java25Home=${java25.get().metadata.installationPath.asFile.absolutePath}",
        "-Djavafmt.test.olderJavaHome=${older.get().metadata.installationPath.asFile.absolutePath}",
    )
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs real Gradle builds against the plugin with Gradle TestKit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
    systemProperty("javafmt.test.minimumGradleVersion", libs.versions.gradlePluginMinimumGradle.get())
    jvmArgumentProviders.add(objects.newInstance<JavaHomes>().apply {
        java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        older = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    })
}

tasks.named("check") {
    dependsOn(functionalTestTask)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    classDirectories.setFrom(sourceSets.main.map { it.output.classesDirs }, engine.output.classesDirs)
    sourceDirectories.setFrom(sourceSets.main.map { it.allJava.srcDirs }, engine.allJava.srcDirs)
}

gradlePlugin {
    website = "https://javafmt.io"
    vcsUrl = "https://github.com/javafmt-io/javafmt"
    testSourceSets(functionalTest)
    plugins {
        create("javafmt") {
            id = "io.javafmt"
            implementationClass = "io.javafmt.gradle.JavafmtPlugin"
            displayName = "javafmt"
            description = project.description
            tags = listOf("java", "formatter", "format", "code-style", "lint")
        }
    }
}
