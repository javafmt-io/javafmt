object ScmVersion {

    private const val INITIAL = "0.1.0-SNAPSHOT"

    private val describePattern = Regex("""^v(.+)-(\d+)-g[0-9a-f]+(-dirty)?$""")

    private val releasePattern = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

    fun fromDescribe(describe: String?): String {
        val match = describePattern.matchEntire(describe.orEmpty().trim()) ?: return INITIAL
        val (tag, commitsSinceTag, dirty) = match.destructured
        if (commitsSinceTag == "0" && dirty.isEmpty()) {
            return tag
        }
        val release = releasePattern.matchEntire(tag) ?: return "$tag-SNAPSHOT"
        val (major, minor, patch) = release.destructured
        return "$major.$minor.${patch.toInt() + 1}-SNAPSHOT"
    }
}
