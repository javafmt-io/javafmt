plugins {
    base
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.axionRelease)
    alias(libs.plugins.llmBuildCompactor)
}

scmVersion {
    tag {
        prefix.set("v")
    }
}

version = (findProperty("version") as? String)
    ?.takeIf { it.isNotBlank() && it != "unspecified" }
    ?: scmVersion.version
description = "An opinionated, high-performance Java code formatter for modern LTS (Java 25+)."
