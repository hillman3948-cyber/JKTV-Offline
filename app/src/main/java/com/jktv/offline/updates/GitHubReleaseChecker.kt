package com.jktv.offline.updates

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import com.jktv.offline.BuildConfig

data class GitHubAppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkFileName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String = "",
    val publishedAt: String = ""
)

class GitHubReleaseChecker(private val context: Context, private val repository: String) {
    suspend fun checkForAppUpdate(
        currentVersion: String,
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): GitHubAppUpdate? = withContext(Dispatchers.IO) {
        require(repository.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) {
            "Set jktvGithubRepository to hillman3948-cyber/JKTV-Offline before checking GitHub releases."
        }
        AppReleaseCatalog.findUpdate(
            getJsonArray("https://api.github.com/repos/$repository/releases?per_page=100"),
            context.packageName, currentVersion, currentVersionCode, ::getJsonObject
        )
    }
    /**
     * Checks if the GitHub repository's releases host a `library-manifest.json` asset for offline song/SF2 packages.
     */
    suspend fun findGitHubLibraryManifestUrl(): String? = withContext(Dispatchers.IO) {
        if (!repository.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) return@withContext null
        runCatching {
            val releases = getJsonArray("https://api.github.com/repos/$repository/releases?per_page=30")
            for (release in releases.takeObjects()) {
                if (release.optBoolean("draft", true)) continue
                val assets = release.optJSONArray("assets") ?: continue
                val manifestAsset = assets.findAsset("library-manifest.json")
                if (manifestAsset != null) {
                    return@runCatching checkedHttps(manifestAsset.getString("browser_download_url"))
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Resumable HTTPS APK download with interrupted download recovery (`Range` header)
     * and strict SHA-256 integrity verification before handoff to Android PackageInstaller.
     */
    suspend fun download(update: GitHubAppUpdate, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val destination = File(directory, update.apkFileName)
        if (destination.isFile && destination.length() > 0L) {
            if (update.sha256.isBlank() || sha256(destination).equals(update.sha256, true)) {
                onProgress(destination.length(), destination.length())
                return@withContext destination
            } else {
                destination.delete()
            }
        }

        val temporary = File(directory, update.apkFileName + ".part")
        val existingBytes = if (temporary.isFile) temporary.length().coerceAtLeast(0L) else 0L

        val connection = (URL(checkedHttps(update.apkUrl)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 90_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "JKTV-Offline-Karaoke")
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }
        try {
            val code = connection.responseCode
            require(code in 200..299 || code == HttpURLConnection.HTTP_PARTIAL) {
                "APK download failed with HTTP $code."
            }
            require(connection.url.protocol.equals("https", true)) { "APK download redirected away from HTTPS." }
            val append = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
            val startBytes = if (append) existingBytes else 0L
            val contentLength = connection.contentLengthLong
            val expectedTotal = if (contentLength > 0L) startBytes + contentLength else -1L

            FileOutputStream(temporary, append).use { output ->
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = startBytes
                    onProgress(downloaded, expectedTotal)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (expectedTotal > 0L && downloaded > expectedTotal) {
                            temporary.delete()
                            throw IllegalStateException("APK download exceeded its reported size.")
                        }
                        output.write(buffer, 0, count)
                        onProgress(downloaded, expectedTotal)
                    }
                    output.fd.sync()
                }
            }
            if (expectedTotal > 0L) {
                require(temporary.length() == expectedTotal) { "APK download was interrupted. Tap Download again to resume." }
            }
            if (update.sha256.isNotBlank()) {
                val actualSha = sha256(temporary)
                if (!actualSha.equals(update.sha256, true)) {
                    temporary.delete()
                    throw IllegalStateException("APK SHA-256 verification failed.")
                }
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Could not finalize the verified APK download." }
            destination
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
        } finally {
            connection.disconnect()
        }
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
        } finally {
            connection.disconnect()
        }
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
