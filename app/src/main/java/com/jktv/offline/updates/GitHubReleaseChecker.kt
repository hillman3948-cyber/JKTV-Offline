package com.jktv.offline.updates

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class GitHubAppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkFileName: String,
    val apkUrl: String,
    val sha256: String
)

class GitHubReleaseChecker(private val context: Context, private val repository: String) {
    suspend fun checkForAppUpdate(currentVersion: String, currentVersionCode: Int): GitHubAppUpdate? = withContext(Dispatchers.IO) {
        require(repository.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) {
            "Set jktvGithubRepository to hillman3948-cyber/JKTV-Offline before checking GitHub releases."
        }
        val releases = getJsonArray("https://api.github.com/repos/$repository/releases?per_page=100")
        val current = releases.takeObjects()
            .filter { !it.optBoolean("draft", true) && it.optString("tag_name").startsWith("app-v") }
            .mapNotNull { release -> release to release.optString("tag_name").removePrefix("app-v") }
            .filter { (_, version) -> versionMatches(version) }
            .maxWithOrNull { left, right -> compareVersions(left.second, right.second) }
            ?: return@withContext null
        val assets = current.first.optJSONArray("assets") ?: throw IllegalStateException("The latest app release has no downloadable assets.")
        val versionMetadataAsset = assets.findAsset("app-version.json")
            ?: throw IllegalStateException("The app release is missing app-version.json.")
        val metadataUrl = checkedHttps(versionMetadataAsset.getString("browser_download_url"))
        val metadata = getJsonObject(metadataUrl)
        require(metadata.optString("packageName") == context.packageName) {
            "The GitHub release is for a different Android app package."
        }
        val versionName = metadata.getString("versionName")
        val versionCode = metadata.getInt("versionCode")
        require(versionName == current.second && compareVersions(versionName, currentVersion) >= 0) {
            "The release version metadata does not match its tag."
        }
        require(versionCode > 0) { "The release has an invalid Android version code." }
        if (compareVersions(versionName, currentVersion) < 0 ||
            (compareVersions(versionName, currentVersion) == 0 && versionCode <= currentVersionCode)
        ) return@withContext null
        val apkName = metadata.getString("apk")
        require(apkName == File(apkName).name && apkName.endsWith(".apk", true)) { "The release has an unsafe APK filename." }
        val apkAsset = assets.findAsset(apkName) ?: throw IllegalStateException("The signed APK is missing from the release.")
        val sha256 = metadata.getString("sha256").lowercase(Locale.ROOT)
        require(sha256.matches(Regex("[a-f0-9]{64}"))) { "The release has an invalid APK checksum." }
        GitHubAppUpdate(versionName, versionCode, apkName, checkedHttps(apkAsset.getString("browser_download_url")), sha256)
    }

    suspend fun download(update: GitHubAppUpdate, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val destination = File(directory, update.apkFileName)
        val temporary = File(directory, update.apkFileName + ".part")
        val connection = (URL(checkedHttps(update.apkUrl)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 90_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            require(connection.responseCode in 200..299) { "APK download failed with HTTP ${connection.responseCode}." }
            require(connection.url.protocol.equals("https", true)) { "APK download redirected away from HTTPS." }
            val expectedLength = connection.contentLengthLong
            FileOutputStream(temporary, false).use { output ->
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (expectedLength > 0 && downloaded > expectedLength) throw IllegalStateException("APK download exceeded its reported size.")
                        output.write(buffer, 0, count)
                        onProgress(downloaded, expectedLength)
                    }
                    output.fd.sync()
                }
            }
            if (expectedLength > 0) require(temporary.length() == expectedLength) { "APK download was incomplete." }
            require(sha256(temporary).equals(update.sha256, true)) { "APK SHA-256 verification failed." }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Could not finalize the verified APK download." }
            destination
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun getJsonArray(address: String): JSONArray {
        val connection = (URL(checkedHttps(address)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "JKTV-Offline-Karaoke")
        }
        try {
            require(connection.responseCode in 200..299) { "GitHub release check failed with HTTP ${connection.responseCode}." }
            require(connection.url.protocol.equals("https", true)) { "GitHub redirected away from HTTPS." }
            return JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun getJsonObject(address: String): JSONObject {
        val connection = (URL(checkedHttps(address)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "JKTV-Offline-Karaoke")
        }
        try {
            require(connection.responseCode in 200..299) { "Could not read GitHub version metadata (HTTP ${connection.responseCode})." }
            require(connection.url.protocol.equals("https", true)) { "Version metadata redirected away from HTTPS." }
            return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }

    private fun JSONArray.takeObjects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.findAsset(name: String): JSONObject? = (0 until length())
        .map { getJSONObject(it) }
        .firstOrNull { it.optString("name") == name }

    private fun checkedHttps(address: String): String {
        val url = URL(address)
        require(url.protocol.equals("https", true) && !url.host.isNullOrBlank()) { "GitHub links must use HTTPS." }
        return address
    }

    private fun versionMatches(value: String): Boolean = value.matches(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+([.-][A-Za-z0-9.-]+)?$"))

    private fun compareVersions(left: String, right: String): Int {
        val a = Regex("\\d+").findAll(left).map { it.value.toLongOrNull() ?: 0L }.toList()
        val b = Regex("\\d+").findAll(right).map { it.value.toLongOrNull() ?: 0L }.toList()
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(index) { 0L }.compareTo(b.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        return when {
            left.contains('-') && !right.contains('-') -> -1
            !left.contains('-') && right.contains('-') -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
