import java.io.File
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JavafmtFormatterTest {

    @Test
    fun `creating the step does not read formatter jars that have not been built yet`(@TempDir dir: File) {
        val unbuilt = dir.resolve("javafmt-spotless.jar")

        assertThatCode { JavafmtFormatter.step(listOf(unbuilt)) }.doesNotThrowAnyException()
    }
}
