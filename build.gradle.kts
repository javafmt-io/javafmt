plugins {
    base
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.axionRelease)
}

scmVersion {
    tag {
        prefix.set("v")
    }
}

version = scmVersion.version
description = "An opinionated, high-performance Java code formatter for modern LTS (Java 21+)."
