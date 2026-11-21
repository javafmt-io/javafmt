import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

abstract class FilePathArgument : CommandLineArgumentProvider {
    @get:Input
    abstract val propertyName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val files: ConfigurableFileCollection

    override fun asArguments() = listOf("-D${propertyName.get()}=${files.singleFile.absolutePath}")
}

abstract class ClasspathArgument : CommandLineArgumentProvider {
    @get:Input
    abstract val propertyName: Property<String>

    @get:Classpath
    abstract val files: ConfigurableFileCollection

    override fun asArguments() = listOf("-D${propertyName.get()}=${files.files.joinToString(File.pathSeparator) { it.absolutePath }}")
}
