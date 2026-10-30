import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

abstract class ToolchainsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")
    }
}
