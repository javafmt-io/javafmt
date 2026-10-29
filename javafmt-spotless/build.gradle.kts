plugins {
    id("javafmt.java-conventions")
    id("javafmt.publish-conventions")
}

description = "Spotless integration for javafmt."

dependencies {
    api(project(":javafmt-core"))
    compileOnly(libs.spotless.lib)
    testImplementation(libs.spotless.lib)
    testImplementation(testFixtures(project(":javafmt-core")))
    // FormatterFunc.NeedsFile reaches into com.diffplug.spotless.Formatter which static-inits slf4j.
    testRuntimeOnly(libs.slf4j.simple)
}

// Everything above is exercised by calling FormatterStep.format directly. What decides whether a
// Gradle user's build works is the harness around it — the buildscript classpath, Spotless's
// up-to-date check, the configuration cache — so integrationTest drives a real Gradle build with
// Gradle TestKit instead.
val integrationTest: SourceSet by sourceSets.creating

/**
 * The Spotless Gradle plugin, fetched so the generated build can put it on its buildscript
 * classpath as files. Resolving it there would need a repository and a network; injecting it keeps
 * the TestKit build offline.
 */
val spotlessPluginRuntime: Configuration by configurations.creating

dependencies {
    spotlessPluginRuntime("com.diffplug.spotless:spotless-plugin-gradle:${libs.versions.spotlessGradle.get()}")
    add(integrationTest.implementationConfigurationName, gradleTestKit())
    add(integrationTest.implementationConfigurationName, platform(libs.junit.bom))
    add(integrationTest.implementationConfigurationName, libs.junit.jupiter)
    add(integrationTest.implementationConfigurationName, libs.assertj.core)
    add(integrationTest.implementationConfigurationName, libs.spotless.lib)
    add(integrationTest.implementationConfigurationName, project(":javafmt-core"))
    add(integrationTest.implementationConfigurationName, testFixtures(project(":javafmt-core")))
    add(integrationTest.implementationConfigurationName, sourceSets.main.get().output)
    add(integrationTest.runtimeOnlyConfigurationName, libs.junit.platform.launcher)
    add(integrationTest.runtimeOnlyConfigurationName, libs.slf4j.simple)
}

// What the generated build's `buildscript { classpath files(...) }` gets: javafmt-spotless and
// everything it needs, the integration-test classes (for the version-pinned step), and the
// Spotless plugin itself.
val generatedBuildClasspath = files(
    sourceSets.main.map { it.output },
    integrationTest.output,
    configurations.named("runtimeClasspath"),
    spotlessPluginRuntime,
)

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs a real Gradle build against the published step with Gradle TestKit."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    inputs.files(generatedBuildClasspath).withNormalizer(ClasspathNormalizer::class)
    // Resolved as the task is realized rather than inside a doFirst: a closure written in the
    // build script cannot be stored in the configuration cache.
    systemProperty("javafmt.generatedBuildClasspath", generatedBuildClasspath.asPath)
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}
