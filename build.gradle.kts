plugins {
    base
    alias(libs.plugins.jreleaser)
}

description = providers.gradleProperty("javafmt.description").get()

