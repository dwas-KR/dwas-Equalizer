package kr.dwas.dwas_EQ.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(
    val version: ReleaseVersion,
    val tagName: String,
    val releaseName: String,
    val releaseUrl: String,
)

object GitHubUpdateChecker {
    private const val RELEASES_API = "https://api.github.com/repos/dwas-KR/dwas-Equalizer/releases?per_page=20"

    suspend fun check(currentVersionName: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val current = ReleaseVersion.parseOrNull(currentVersionName) ?: return@runCatching null
            val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "dwas_EQ/$currentVersionName")
                if (connection.responseCode !in 200..299) return@runCatching null
                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                newestStable(payload, current)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    internal fun newestStable(payload: String, current: ReleaseVersion): AvailableUpdate? {
        val releases = JSONArray(payload)
        var best: AvailableUpdate? = null
        for (index in 0 until releases.length()) {
            val item = releases.optJSONObject(index) ?: continue
            if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue
            val tag = item.optString("tag_name")
            val version = ReleaseVersion.parseOrNull(tag) ?: continue
            if (version <= current || best?.version?.let { version <= it } == true) continue
            val url = item.optString("html_url")
            if (url.isBlank()) continue
            best = AvailableUpdate(
                version = version,
                tagName = tag,
                releaseName = item.optString("name").ifBlank { tag },
                releaseUrl = url,
            )
        }
        return best
    }
}
