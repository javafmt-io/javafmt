rootProject.name = "javafmt"

include("javafmt-core", "javafmt-cli", "javafmt-spotless", "javafmt-intellij", "javafmt-maven-plugin")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
