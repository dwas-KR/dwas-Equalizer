package kr.dwas.dwas_EQ.update

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): ReleaseVersion = parseOrNull(value) ?: error("Invalid release version: $value")

        fun parseOrNull(value: String): ReleaseVersion? {
            val match = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matchEntire(value.trim()) ?: return null
            return ReleaseVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}
