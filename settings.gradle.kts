rootProject.name = "javafmt"

include("javafmt-core", "javafmt-cli", "javafmt-spotless", "javafmt-intellij", "javafmt-maven-plugin", "javafmt-vscode", "website")

dependencyResolutionManagement {
    // PREFER_PROJECT lets javafmt-intellij declare IntelliJ Platform repos at the project
    // level (required by the IntelliJ Platform Gradle Plugin). All other modules inherit
    // mavenCentral() from here since they declare no repositories of their own.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}
