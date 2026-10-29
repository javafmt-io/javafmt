import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.DevelocityPlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

abstract class BuildScansPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.pluginManager.apply(DevelocityPlugin::class.java)
        val ci = settings.providers.environmentVariable("CI").isPresent
        settings.extensions.configure(DevelocityConfiguration::class.java) {
            buildScan {
                termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
                termsOfUseAgree.set("yes")
                publishing.onlyIf { ci }
                uploadInBackground.set(!ci)
            }
        }
    }
}
