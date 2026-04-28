rootProject.name = "grind"

include("grind-core", "grind-cli", "grind-spotless", "grind-intellij", "grind-maven-plugin")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
