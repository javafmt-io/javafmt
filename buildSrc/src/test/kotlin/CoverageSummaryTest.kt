import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoverageSummaryTest {

    private val doctype = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">"""

    @Test
    fun `parse reads the report name and only the report-level counters`() {
        val xml = doctype + """
            <report name="javafmt-core">
              <package name="io/javafmt">
                <counter type="LINE" missed="99" covered="1"/>
              </package>
              <counter type="INSTRUCTION" missed="10" covered="90"/>
              <counter type="BRANCH" missed="1" covered="3"/>
              <counter type="LINE" missed="5" covered="15"/>
            </report>
        """.trimIndent()

        assertThat(CoverageSummary.parse(xml)).isEqualTo(
            ModuleCoverage(
                "javafmt-core",
                mapOf(
                    "INSTRUCTION" to Counter(covered = 90, missed = 10),
                    "BRANCH" to Counter(covered = 3, missed = 1),
                    "LINE" to Counter(covered = 15, missed = 5),
                ),
            ),
        )
    }

    @Test
    fun `render sorts modules, adds a total row, and shows n-a for a counter with nothing to count`() {
        val modules = listOf(
            ModuleCoverage("javafmt-spotless", mapOf("LINE" to Counter(3, 1), "INSTRUCTION" to Counter(1, 1))),
            ModuleCoverage("javafmt-cli", mapOf("LINE" to Counter(1, 0), "BRANCH" to Counter(1, 3), "INSTRUCTION" to Counter(2, 0))),
        )

        assertThat(CoverageSummary.render(modules)).isEqualTo(
            """
            ## Coverage

            | Module | Lines | Branches | Instructions |
            |---|---|---|---|
            | javafmt-cli | 100.0% | 25.0% | 100.0% |
            | javafmt-spotless | 75.0% | n/a | 50.0% |
            | **Total** | 80.0% | 25.0% | 75.0% |

            """.trimIndent(),
        )
    }

    @Test
    fun `render says so when there are no reports`() {
        assertThat(CoverageSummary.render(listOf())).isEqualTo("## Coverage\n\nNo JaCoCo reports found.\n")
    }
}
