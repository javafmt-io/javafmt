plugins {
    base
}

description = providers.gradleProperty("javafmt.description").get()

val coverageReports = configurations.create("coverageReports") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("jacoco-xml-report"))
    }
}

dependencies {
    listOf("javafmt-cli", "javafmt-core", "javafmt-maven-plugin", "javafmt-spotless", "javafmt-vscode").forEach {
        coverageReports(project(":$it"))
    }
}

val coverageSummary = tasks.register<CoverageSummaryTask>("coverageSummary") {
    reports.from(coverageReports)
    summary = layout.buildDirectory.file("reports/coverage/summary.md")
}

tasks.named("check") {
    dependsOn(coverageSummary)
}
