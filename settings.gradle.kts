rootProject.name = "grind"

include("grind-core", "grind-cli", "grind-spotless", "grind-intellij", "grind-maven-plugin")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
