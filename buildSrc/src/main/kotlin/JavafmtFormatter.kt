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
    private const val APPLY_WITH_ANALYZE_METHOD = "applyWithAnalyze"

    private val loaderCache = ConcurrentHashMap<List<JarEntry>, URLClassLoader>()

    /**
     * Builds a Spotless `FormatterStep` whose state is the absolute paths of
     * the jars that make up the javafmt-spotless runtime classpath, plus a
     * SHA-256 of each jar. The hash is what lets Spotless's task-cache notice
     * a rebuild of the formatter jar at the same path — without it, edits to
     * the formatter wouldn't propagate to a long-lived Gradle daemon.
     *
     * When [analysisClasspath] is provided the step calls [APPLY_WITH_ANALYZE_METHOD]
     * instead of [APPLY_METHOD], enabling attribution-dependent lint rules such as
     * [RedundantTypeArguments]'s witness-drop and [LocalVarUseVar]'s widening-assignment path.
     */
    fun step(jars: Collection<File>, analysisClasspath: Collection<File> = emptyList()): FormatterStep {
        val classpathStr = analysisClasspath.joinToString(File.pathSeparator) { it.absolutePath }
        val state = ClasspathState(ArrayList(jars.map { JarEntry(it.absolutePath, sha256(it)) }), classpathStr)
        return FormatterStep.create("javafmt", state, FormatterFactory)
    }

    /**
     * Identifies a single jar on the formatter classpath by path AND content hash.
     * Including the hash means the [ClasspathState] changes whenever the jar's
     * bytes change, invalidating both Spotless's on-disk task cache (which keys
     * on the serialized state) and our in-process [loaderCache] (which keys on
     * the list of entries).
     */
    private data class JarEntry(val path: String, val sha256: String) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    private data class ClasspathState(val entries: ArrayList<JarEntry>, val analysisClasspath: String?) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 2L
        }
    }

    /**
     * Top-level serializable function that materializes the [ClasspathState]
     * into a [FormatterFunc] at step-execution time. Defined as a Kotlin
     * singleton so it has stable identity for Java serialization (no captured
     * outer references).
     */
    private object FormatterFactory : SerializedFunction<ClasspathState, FormatterFunc> {

        private const val serialVersionUID: Long = 1L

        override fun apply(state: ClasspathState): FormatterFunc {
            val loader = loaderCache.computeIfAbsent(state.entries) { entries ->
                val urls = entries.map { File(it.path).toURI().toURL() }.toTypedArray()
                // Use platform parent to keep Gradle's classes out of the formatter's
                // classpath and avoid version-skew on slf4j etc.
                URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
            }
            val clazz = Class.forName(FORMATTER_CLASS, true, loader)
            if (!state.analysisClasspath.isNullOrEmpty()) {
                val method = clazz.getMethod(APPLY_WITH_ANALYZE_METHOD, String::class.java, String::class.java)
                val classpath = state.analysisClasspath
                return FormatterFunc { input ->
                    try {
                        method.invoke(null, input, classpath) as String
                    } catch (t: ReflectiveOperationException) {
                        val cause = t.cause ?: t
                        throw RuntimeException("javafmt formatter invocation failed: ${cause.message}", cause)
                    }
                }
            }
            val method = clazz.getMethod(APPLY_METHOD, String::class.java)
            return FormatterFunc { input ->
                try {
                    method.invoke(null, input) as String
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
