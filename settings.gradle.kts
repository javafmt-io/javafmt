rootProject.name = "javafmt"

include("javafmt-core", "javafmt-cli", "javafmt-spotless", "javafmt-intellij", "javafmt-maven-plugin", "javafmt-vscode")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
