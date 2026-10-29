import java.time.Instant
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.withType

abstract class ReproducibleArchivesPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.lifecycle.beforeProject {
            val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH")
                .map { Instant.ofEpochSecond(it.trim().toLong()).toEpochMilli() }
            tasks.withType<AbstractArchiveTask>().configureEach {
                reproducibleFileTimestamp.set(sourceDateEpoch)
            }
        }
    }
}
