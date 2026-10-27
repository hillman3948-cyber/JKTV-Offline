package com.jktv.offline.updates

import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Pure release selection, shared by automatic and manual checks. */
object AppReleaseCatalog {
    private val stableVersion = Regex("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")
    fun compareVersions(left: String, right: String): Int {
        val a = left.substringBefore('-').split('.').map { it.toLongOrNull() ?: 0 }
        val b = right.substringBefore('-').split('.').map { it.toLongOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    fun findUpdate(releases: JSONArray, packageName: String, currentVersion: String, currentCode: Int,
                   metadata: (String) -> JSONObject): GitHubAppUpdate? {
        val candidates = (0 until releases.length()).map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft", true) && !it.optBoolean("prerelease", false) }
            .mapNotNull { release ->
                val tag = release.optString("tag_name")
                val version = when {
                    tag.startsWith("app-v") -> tag.removePrefix("app-v")
                    tag.startsWith("v") -> tag.removePrefix("v")
                    else -> ""
                }
                if (stableVersion.matches(version) && compareVersions(version, currentVersion) >= 0)
                    release to version else null
            }.sortedWith { a, b -> compareVersions(b.second, a.second) }
        var best: GitHubAppUpdate? = null
        for ((release, tagVersion) in candidates) {
            if (best != null && compareVersions(tagVersion, best.versionName) < 0) break
            val assets = release.optJSONArray("assets") ?: continue
            fun asset(name: String) = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == name }
            val descriptor = asset("app-version.json") ?: continue
            val version = metadata(https(descriptor.getString("browser_download_url")))
            if (version.optString("packageName") != packageName) continue
            val name = version.getString("versionName")
            val code = version.getInt("versionCode")
            require(name == tagVersion && code > 0) { "Release version metadata does not match its tag or build number." }
            // Android requires a greater build code, including a same-name maintenance release.
            if (code <= currentCode) continue
            val filename = version.getString("apk")
            require(filename.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk"))) { "The release has an unsafe APK filename." }
            val apk = asset(filename) ?: error("The release APK is missing.")
            val digest = version.getString("sha256").lowercase(java.util.Locale.ROOT)
            require(digest.matches(Regex("[a-f0-9]{64}"))) { "The release has an invalid APK checksum." }
            val update = GitHubAppUpdate(name, code, filename, https(apk.getString("browser_download_url")), digest,
                release.optString("body").trim(), release.optString("published_at").take(10))
            if (best == null || compareVersions(name, best.versionName) > 0 || code > best.versionCode) best = update
        }
        return best
    }

    private fun https(value: String): String {
        val url = URL(value)
        require(url.protocol == "https" && url.host.isNotBlank()) { "GitHub update links must use HTTPS." }
        return value
    }
}
