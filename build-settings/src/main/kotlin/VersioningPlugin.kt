import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

abstract class VersioningPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val providers = settings.providers
        val scmVersion = providers.of(ScmVersionSource::class.java) {
            parameters.repository.set(settings.settingsDir)
        }
        val projectVersion = providers.gradleProperty("version")
            .filter { it.isNotBlank() && it != "unspecified" }
            .orElse(scmVersion)
            .get()

        settings.gradle.lifecycle.beforeProject {
            version = projectVersion
            if (path == ":") {
                tasks.register("currentVersion") {
                    group = "help"
                    description = "Prints the version derived from the latest v* git tag."
                    doLast {
                        println(projectVersion)
                    }
                }
            }
        }
    }
}
