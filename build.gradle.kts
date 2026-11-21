plugins {
    base
}

val coverageReportDependencies = configurations.dependencyScope("coverageReportDependencies")

val coverageReports = configurations.resolvable("coverageReports") {
    extendsFrom(coverageReportDependencies.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("jacoco-xml-report"))
    }
}

dependencies {
    listOf("javafmt-cli", "javafmt-core", "javafmt-daemon", "javafmt-maven-plugin", "javafmt-spotless").forEach {
        coverageReportDependencies(project(":$it"))
    }
}

val coverageSummary = tasks.register<CoverageSummaryTask>("coverageSummary") {
    reports.from(coverageReports)
    summary = layout.buildDirectory.file("reports/coverage/summary.md")
}

tasks.named("check") {
    dependsOn(coverageSummary)
}
