import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ScmVersionTest {

    @ParameterizedTest
    @CsvSource(
        nullValues = ["null"],
        value = [
            "v0.3.1-0-g1a2b3c4,        0.3.1",
            "v0.3.1-0-g1a2b3c4-dirty,  0.3.2-SNAPSHOT",
            "v0.3.1-4-g1a2b3c4,        0.3.2-SNAPSHOT",
            "v1.2.9-12-g1a2b3c4-dirty, 1.2.10-SNAPSHOT",
            "v2.0.0-rc.1-0-g1a2b3c4,   2.0.0-rc.1",
            "v2.0.0-rc.1-2-g1a2b3c4,   2.0.0-rc.1-SNAPSHOT",
            "null,                     0.1.0-SNAPSHOT",
            "'',                       0.1.0-SNAPSHOT",
        ],
    )
    fun `derives the version from git describe output`(describe: String?, expected: String) {
        assertThat(ScmVersion.fromDescribe(describe)).isEqualTo(expected)
    }
}
