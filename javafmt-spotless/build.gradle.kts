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
}

// Everything above is exercised by calling FormatterStep.format directly. What decides whether a
// Gradle user's build works is the harness around it — the buildscript classpath, Spotless's
// up-to-date check, the configuration cache — so integrationTest drives a real Gradle build with
// Gradle TestKit instead.

/**
 * The Spotless Gradle plugin, fetched so the generated build can put it on its buildscript
 * classpath as files. Resolving it there would need a repository and a network; injecting it keeps
 * the TestKit build offline.
 */
val spotlessPluginRuntime = configurations.create("spotlessPluginRuntime")

dependencies {
    spotlessPluginRuntime(libs.spotless.gradle.plugin)
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            // What the generated build's `buildscript { classpath files(...) }` gets: javafmt-spotless and
            // everything it needs, the integration-test classes (for the version-pinned step), and the
            // Spotless plugin itself.
            val generatedBuildClasspath = files(
                sourceSets.main.map { it.output },
                sources.output,
                configurations.named("runtimeClasspath"),
                spotlessPluginRuntime,
            )
            dependencies {
                implementation(project())
                implementation(libs.spotless.lib)
                implementation(testFixtures(project(":javafmt-core")))
                implementation(gradleTestKit())
            }
            targets {
                all {
                    testTask.configure {
                        description = "Runs a real Gradle build against the published step with Gradle TestKit."
                        jvmArgumentProviders.add(objects.newInstance<ClasspathArgument>().apply {
                            propertyName = "javafmt.generatedBuildClasspath"
                            files.from(generatedBuildClasspath)
                        })
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}
