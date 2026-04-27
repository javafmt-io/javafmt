import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("grind.java-conventions")
    alias(libs.plugins.jmh)
}

tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("compileJmh") || name == "jmhCompileGeneratedClasses") {
        options.errorprone {
            check("NullAway", CheckSeverity.OFF)
        }
    }
}

// Enable async-profiler with: gradle :grind-core:jmh -PjmhProfile=async
// Optional: -PjmhInclude=GrindFormatBenchmark.* to scope the run.
// Requires async-profiler installed via `brew install async-profiler` (path below is for Apple Silicon homebrew).
if (project.hasProperty("jmhProfile") && project.property("jmhProfile") == "async") {
    val asyncLib = "/opt/homebrew/opt/async-profiler/lib/libasyncProfiler.dylib"
    val outputDir = layout.buildDirectory.dir("jmh-async").get().asFile.absolutePath
    jmh {
        profilers.add("async:libPath=$asyncLib;output=flamegraph,collapsed;dir=$outputDir")
        warmupIterations.set(1)
        iterations.set(3)
        fork.set(1)
        if (project.hasProperty("jmhInclude")) {
            includes.add(project.property("jmhInclude") as String)
        }
    }
}
