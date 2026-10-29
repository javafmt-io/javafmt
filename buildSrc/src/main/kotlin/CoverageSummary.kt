import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class Counter(val covered: Long, val missed: Long)

data class ModuleCoverage(val module: String, val counters: Map<String, Counter>)

object CoverageSummary {

    private val columns = listOf("LINE" to "Lines", "BRANCH" to "Branches", "INSTRUCTION" to "Instructions")

    fun parse(xml: String): ModuleCoverage {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val report = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        val counters = generateSequence(report.firstChild) { it.nextSibling }
            .filterIsInstance<Element>()
            .filter { it.tagName == "counter" }
            .associate { it.getAttribute("type") to Counter(it.getAttribute("covered").toLong(), it.getAttribute("missed").toLong()) }
        return ModuleCoverage(report.getAttribute("name"), counters)
    }

    fun render(modules: List<ModuleCoverage>): String {
        if (modules.isEmpty()) {
            return "## Coverage\n\nNo JaCoCo reports found.\n"
        }
        val total = ModuleCoverage(
            "**Total**",
            columns.associate { (type, _) ->
                type to Counter(modules.sumOf { it.counter(type).covered }, modules.sumOf { it.counter(type).missed })
            },
        )
        val rows = (modules.sortedBy { it.module } + total).map { module ->
            "| ${module.module} | " + columns.joinToString(" | ") { (type, _) -> percent(module.counter(type)) } + " |"
        }
        val header = listOf(
            "| Module | " + columns.joinToString(" | ") { it.second } + " |",
            "|---|" + columns.joinToString("") { "---|" },
        )
        return (listOf("## Coverage", "") + header + rows).joinToString("\n", postfix = "\n")
    }

    private fun ModuleCoverage.counter(type: String) = counters[type] ?: Counter(0, 0)

    private fun percent(counter: Counter): String {
        val total = counter.covered + counter.missed
        return if (total == 0L) "n/a" else "%.1f%%".format(java.util.Locale.ROOT, 100.0 * counter.covered / total)
    }
}

@CacheableTask
abstract class CoverageSummaryTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val reports: ConfigurableFileCollection

    @get:OutputFile
    abstract val summary: RegularFileProperty

    @TaskAction
    fun write() {
        val modules = reports.files.filter { it.isFile }.map { CoverageSummary.parse(it.readText()) }
        summary.get().asFile.writeText(CoverageSummary.render(modules))
    }
}
