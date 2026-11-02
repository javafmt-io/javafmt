import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import com.diffplug.spotless.SerializedFunction
import java.io.File
import java.io.Serializable
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge used by the spotless convention plugin to invoke the just-built
 * `javafmt-spotless` formatter on the project's own sources.
 *
 * `io.javafmt.spotless.JavafmtFormatterStep` is *not* available on Gradle's
 * daemon classpath — there is no clean way to put it there without a project
 * dependency cycle (`:javafmt-core` would depend on `:javafmt-spotless`, which
 * depends back on `:javafmt-core`). Instead we resolve the formatter jars via
 * a `Configuration` (`javafmtFormatterRuntime`), capture their absolute paths
 * in a small serializable [ClasspathState], and at step-execution time spin up
 * an isolated `URLClassLoader` over those paths to load `JavafmtFormatterStep`
 * reflectively.
 *
 * We don't use Spotless's built-in [com.diffplug.spotless.JarState] because
 * it stores the file list transiently and reconstructs it via Maven
 * coordinates on deserialization — fine for off-the-shelf steps, but
 * incompatible with `preserveOrder(Collection<File>)` over locally-built
 * jars whose paths are not Maven-addressable. The classloader is cached
 * per classpath fingerprint so repeated calls during a single build reuse it.
 *
 * The result is "format the rest of the tree with the most recently built
 * javafmt-spotless artifact" — exactly the dogfood pattern called out in
 * ROADMAP Phase 2 #3.
 */
object JavafmtFormatter {

    private const val FORMATTER_CLASS = "io.javafmt.spotless.JavafmtFormatterStep"
    private const val APPLY_METHOD = "apply"

    private val loaderCache = ConcurrentHashMap<List<JarEntry>, URLClassLoader>()

    /**
     * Builds a Spotless `FormatterStep` whose state is the absolute paths of
     * the jars that make up the javafmt-spotless runtime classpath. The jars
     * need not exist yet; the spotless tasks declare them as classpath inputs.
     *
     * The step calls [APPLY_METHOD] with each file and [analysisClasspath], so
     * the lint rules that need resolved types run and the `.javafmt` project
     * file that applies to the file is honoured. An empty [analysisClasspath]
     * formats without those rules.
     */
    fun step(jars: Collection<File>, analysisClasspath: Collection<File>, release: () -> Int): FormatterStep {
        val classpathStr = analysisClasspath.joinToString(File.pathSeparator) { it.absolutePath }
        val paths = ArrayList(jars.map { it.absolutePath })
        return FormatterStep.createLazy("javafmt", { ClasspathState(paths, classpathStr, release()) }, FormatterFactory)
    }

    /**
     * Keys [loaderCache] by path and content hash, so a jar rebuilt at the same
     * path gets a fresh class loader in a long-lived daemon.
     */
    private data class JarEntry(val path: String, val sha256: String) : Serializable

    private data class ClasspathState(val jars: ArrayList<String>, val analysisClasspath: String?, val release: Int) : Serializable

    /**
     * Top-level serializable function that materializes the [ClasspathState]
     * into a [FormatterFunc] at step-execution time. Defined as a Kotlin
     * singleton so it has stable identity for Java serialization (no captured
     * outer references).
     */
    private object FormatterFactory : SerializedFunction<ClasspathState, FormatterFunc> {

        override fun apply(state: ClasspathState): FormatterFunc {
            val entries = state.jars.map { JarEntry(it, sha256(File(it))) }
            val loader = loaderCache.computeIfAbsent(entries) { entries ->
                val urls = entries.map { File(it.path).toURI().toURL() }.toTypedArray()
                // Use platform parent to keep Gradle's classes out of the formatter's
                // classpath and avoid version-skew on slf4j etc.
                URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
            }
            val clazz = Class.forName(FORMATTER_CLASS, true, loader)
            val method = clazz.getMethod(APPLY_METHOD, String::class.java, File::class.java, String::class.java, Int::class.javaPrimitiveType)
            val classpath = state.analysisClasspath ?: ""
            return FormatterFunc.NeedsFile { input, file ->
                try {
                    method.invoke(null, input, file, classpath, state.release) as String
                } catch (t: ReflectiveOperationException) {
                    val cause = t.cause ?: t
                    throw RuntimeException("javafmt formatter invocation failed: ${cause.message}", cause)
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
