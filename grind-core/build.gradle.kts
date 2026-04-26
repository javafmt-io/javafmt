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
