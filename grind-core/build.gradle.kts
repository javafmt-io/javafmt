import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("grind.java-conventions")
    alias(libs.plugins.jmh)
}

// JavaParser bypasses JavaCompiler.getTask and parses via com.sun.tools.javac.* internals
// to skip BasicJavacTask.initPlugins (a per-call ServiceLoader walk over javac's URL classpath).
// Required at compile time for source references and at runtime for JVM module access.
val javacExports = listOf(
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.compiler/com.sun.tools.javac.file",
    "jdk.compiler/com.sun.tools.javac.parser",
    "jdk.compiler/com.sun.tools.javac.tree",
    "jdk.compiler/com.sun.tools.javac.util",
)

tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("compileJmh") || name == "jmhCompileGeneratedClasses") {
        options.errorprone {
            check("NullAway", CheckSeverity.OFF)
        }
    }
    options.compilerArgs.addAll(javacExports.flatMap { listOf("--add-exports", "$it=ALL-UNNAMED") })
}

tasks.withType<Test>().configureEach {
    jvmArgs(javacExports.map { "--add-exports=$it=ALL-UNNAMED" })
}

jmh {
    jvmArgsAppend.addAll(javacExports.map { "--add-exports=$it=ALL-UNNAMED" })
}

// Downstream consumers of grind-core get the exports automatically via the JAR manifest (JEP 261).
tasks.named<Jar>("jar") {
    manifest {
        attributes("Add-Exports" to javacExports.joinToString(" "))
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
