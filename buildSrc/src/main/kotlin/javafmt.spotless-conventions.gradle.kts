import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Wires Spotless into the project, configured to run javafmt-spotless against
 * the project's own Java sources. See `JavafmtFormatter.kt` for why we go
 * through a `URLClassLoader` instead of a direct call.
 *
 * `:javafmt-spotless` is carved out — it cannot format itself without depending
 * on its own runtime classpath, which is a circular self-reference. Every other
 * JVM module picks this convention up via `javafmt.java-conventions`.
 */
plugins {
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

// Pulling `:javafmt-spotless` in transitively gives us `:javafmt-core` and
// slf4j-api for free.
//
// This is a one-way dependency from "everything else" -> `:javafmt-spotless`.
// The reverse (`:javafmt-spotless` -> `:javafmt-core`) is a normal compile dep
// declared in `:javafmt-spotless`'s build. Gradle resolves them as separate
// variants; no project cycle results because the resolution graphs do not
// feed each other.
val javafmtFormatterRuntime by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    description = "Classpath used to load io.javafmt.spotless.JavafmtFormatterStep reflectively for the project's own Spotless task."
}

dependencies {
    javafmtFormatterRuntime(project(":javafmt-spotless"))
    // `:javafmt-spotless` declares `spotless-lib` as compileOnly, but the
    // JavafmtFormatterStep class signatures reference `com.diffplug.spotless.FormatterStep`
    // — class linking needs it on the runtime classpath of the reflective loader.
    javafmtFormatterRuntime(libs.findLibrary("spotless-lib").get())
}

configure<SpotlessExtension> {
    java {
        target("src/**/*.java")
        targetExclude(
            // Pre-formatted fixture pairs that intentionally exercise non-canonical input.
            "src/test/resources/test-fixtures/**",
            // Oracle corpus is intentionally unformatted — it is what we measure against.
            "src/oracleTest/resources/corpus/**",
            "**/input.java",
            "**/expected.java",
        )

        // We resolve the formatter jars at configuration time and bake their
        // absolute paths into the spotless step's serializable state. The step
        // then builds an isolated `URLClassLoader` over those paths and calls
        // `JavafmtFormatterStep.applyWithAnalyze(String, String)` reflectively,
        // which enables attribution-dependent lint rules (RedundantTypeArguments
        // witness-drop, LocalVarUseVar widening path). If the classpath is
        // unavailable the step falls back to parse-only formatting per file.
        //
        // We combine compileClasspath (external deps) with the project's own
        // compiled classes so javac can resolve internal types during attribution.
        // The classes directory is empty at configuration time but populated by
        // compileJava before spotless tasks run, so paths are correct at execution.
        val analysisClasspath = try {
            val external = configurations.named("compileClasspath").get().resolve()
            val ownClasses = extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>()
                .sourceSets.getByName("main").output.classesDirs.files
            external + ownClasses
        } catch (_: Exception) {
            emptySet()
        }
        addStep(JavafmtFormatter.step(javafmtFormatterRuntime.resolve(), analysisClasspath))
    }
}

// Make the spotless tasks rebuild the javafmt-spotless jar (and its transitive
// :javafmt-core jar) before running.
tasks.matching { it.name.startsWith("spotless") }.configureEach {
    inputs.files(javafmtFormatterRuntime).withNormalizer(org.gradle.api.tasks.ClasspathNormalizer::class.java)
}
