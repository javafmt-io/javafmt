import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

abstract class ScmVersionSource : ValueSource<String, ScmVersionSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val repository: DirectoryProperty
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String {
        val describe = ByteArrayOutputStream()
        exec.exec {
            commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--long", "--dirty")
            workingDir = parameters.repository.get().asFile
            isIgnoreExitValue = true
            standardOutput = describe
            errorOutput = OutputStream.nullOutputStream()
        }
        return ScmVersion.fromDescribe(describe.toString(Charsets.UTF_8))
    }
}
