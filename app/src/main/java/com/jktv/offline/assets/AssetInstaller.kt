package com.jktv.offline.assets

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import android.os.StatFs
import com.jktv.offline.BuildConfig

data class AssetPackage(
    val id: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val expandedSizeBytes: Long = 0L
)

data class LibraryManifest(
    val libraryVersion: String,
    val minimumAppVersion: String,
    val packages: List<AssetPackage>,
    val deletedFiles: List<String> = emptyList()
)

class AssetInstaller(context: Context) {
    private val root = File(context.filesDir, "karaoke")
    private val staging = File(root, "staging")
    private val downloads = File(root, "downloads")
    private val installed = File(root, "library")
    private val maxExpandedBytes = 2L * 1024 * 1024 * 1024
    private val maxEntries = 100_000

    init {
        val backup = File(root, "library.previous")
        if (!installed.exists() && backup.exists()) backup.renameTo(installed)
        else if (installed.exists()) backup.deleteRecursively()
    }

    suspend fun readManifest(manifestUrl: String): LibraryManifest = withContext(Dispatchers.IO) {
        val url = URL(manifestUrl)
        require(url.protocol.equals("https", true)) { "The asset manifest must use HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
        }
        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "The asset manifest could not be downloaded ($responseCode)." }
            require(connection.url.protocol.equals("https", true)) { "The manifest server redirected away from HTTPS." }
            val manifestText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
            require(manifestText.length <= 4 * 1024 * 1024) { "The asset manifest is too large." }
            val json = JSONObject(manifestText)
            val packages = buildList {
                val fonts = json.optJSONArray("soundfonts")
                if (fonts != null) for (i in 0 until fonts.length()) add(fonts.getJSONObject(i).toPackage())
                val songs = json.optJSONArray("songPackages")
                if (songs != null) for (i in 0 until songs.length()) add(songs.getJSONObject(i).toPackage())
            }
            require(packages.isNotEmpty()) { "The asset manifest has no downloadable packages." }
            require(packages.any { it.fileName.endsWith(".zip", true) } && packages.any { it.fileName.endsWith(".sf2", true) }) {
                "The asset manifest must include a song archive and an SF2 SoundFont."
            }
            require(packages.map { it.id.lowercase(Locale.ROOT) }.distinct().size == packages.size) { "The asset manifest contains duplicate package ids." }
            require(packages.all { it.sizeBytes > 0 && it.sha256.matches(Regex("(?i)[a-f0-9]{64}")) }) { "The manifest has a missing package size or SHA-256 hash." }
            packages.forEach {
                safeName(it.fileName)
                safeName(it.id)
                require(URL(it.url).protocol.equals("https", true)) { "Package URLs must use HTTPS." }
            }
            val minimum = json.optString("minimumAppVersion", "0.1.0")
            require(minimum.matches(Regex("^\\d+(?:\\.\\d+){1,3}$"))) { "The manifest minimum app version is invalid." }
            require(isVersionAtLeast(BuildConfig.VERSION_NAME, minimum)) { "This library requires app version $minimum or later." }
            val deletedFiles = buildList {
                json.optJSONArray("deletedFiles")?.let { entries ->
                    for (i in 0 until entries.length()) add(entries.getString(i))
                }
            }
            deletedFiles.forEach(::safeRelativePath)
            LibraryManifest(json.getString("libraryVersion"), minimum, packages, deletedFiles)
        } finally { connection.disconnect() }
    }

    fun requiredDownloadBytes(manifest: LibraryManifest): Long = missingPackages(manifest).sumOf { it.sizeBytes }

    fun cachedPackageProgress(manifest: LibraryManifest): Map<String, Long> = missingPackages(manifest)
        .mapNotNull { item -> cachedBytes(item).takeIf { it > 0L }?.let { item.id to it } }
        .toMap()

    fun installedLibraryManifest(): LibraryManifest? = File(installed, "manifest.json").takeIf { it.isFile }?.let {
        runCatching { JSONObject(it.readText(Charsets.UTF_8)).toLibraryManifest() }.getOrNull()
    }

    fun requiredStorageBytes(manifest: LibraryManifest): Long {
        val missing = missingPackages(manifest)
        val stagingExpansion = missing.sumOf { item ->
            if (item.fileName.endsWith(".zip", true)) item.expandedSizeBytes else item.sizeBytes
        }
        val downloadBytes = missing.sumOf { item -> (item.sizeBytes - cachedBytes(item)).coerceAtLeast(0L) }
        val existingCopyBytes = if (installed.isDirectory) installed.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        return downloadBytes + stagingExpansion + existingCopyBytes
    }

    private fun missingPackages(manifest: LibraryManifest): List<AssetPackage> {
        val prior = File(installed, "manifest.json").takeIf { it.isFile }?.let {
            runCatching { JSONObject(it.readText(Charsets.UTF_8)).toLibraryManifest() }.getOrNull()
        }
        return manifest.packages.filterNot { item ->
            prior?.packages?.any { it.id == item.id && it.sha256.equals(item.sha256, true) } == true
        }
    }

    private fun cachedBytes(item: AssetPackage): Long {
        val part = File(downloads, safeName(item.id) + "-" + safeName(item.fileName) + ".part")
        return part.length().takeIf { it <= item.sizeBytes } ?: 0L
    }

    suspend fun install(manifest: LibraryManifest, onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }) = withContext(Dispatchers.IO) {
        downloads.mkdirs()
        staging.deleteRecursively()
        staging.mkdirs()
        val priorManifest = File(installed, "manifest.json").takeIf { it.isFile }?.let {
            runCatching { JSONObject(it.readText(Charsets.UTF_8)).toLibraryManifest() }.getOrNull()
        }
        try {
            if (installed.isDirectory) installed.copyRecursively(staging, overwrite = true)
            manifest.packages.forEach { item ->
                if (priorManifest?.packages?.any { it.id == item.id && it.sha256.equals(item.sha256, true) } == true) return@forEach
                val archive = File(downloads, safeName(item.id) + "-" + safeName(item.fileName) + ".part")
                downloadWithRetry(item, archive, onProgress)
                try {
                    verify(archive, item)
                    if (item.fileName.endsWith(".zip", true)) {
                        val target = File(staging, "songs")
                        target.mkdirs()
                        extractSafely(archive, target)
                    } else {
                        val name = safeName(item.fileName)
                        require(name.endsWith(".sf2", true)) { "Only SF2 SoundFonts are supported by this engine." }
                        val header = ByteArray(12)
                        archive.inputStream().use { input -> require(input.read(header) == header.size) { "The SoundFont file is incomplete." } }
                        require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "sfbk") {
                            "${item.fileName} is not a valid SF2 SoundFont."
                        }
                        val target = File(staging, "soundfonts/$name")
                        target.parentFile?.mkdirs()
                        archive.copyTo(target, overwrite = true)
                    }
                } catch (error: Throwable) {
                    archive.delete()
                    throw error
                }
            }
            val packageIds = manifest.packages.map { it.id }.toSet()
            priorManifest?.packages?.filter { it.id !in packageIds }?.forEach { removed ->
                if (!removed.fileName.endsWith(".zip", true)) File(staging, "soundfonts/${safeName(removed.fileName)}").delete()
            }
            priorManifest?.packages?.filter { old ->
                !old.fileName.endsWith(".zip", true) && manifest.packages.any { current -> current.id == old.id && current.sha256 != old.sha256 }
            }?.forEach { replaced -> File(staging, "soundfonts/${safeName(replaced.fileName)}").delete() }
            manifest.deletedFiles.forEach { relative -> File(staging, "songs/${safeRelativePath(relative)}").delete() }
            File(staging, "manifest.json").writeText(manifest.toJson().toString(), Charsets.UTF_8)
            activateStaging()
            downloads.deleteRecursively()
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private suspend fun downloadWithRetry(item: AssetPackage, part: File, onProgress: (String, Long, Long) -> Unit) {
        var lastError: Throwable? = null
        repeat(5) { attempt ->
            try {
                download(item, part, onProgress)
                onProgress(item.id, part.length(), item.sizeBytes)
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                if (attempt < 4) delay(1_000L shl attempt)
            }
        }
        throw IllegalStateException("Could not download ${item.fileName} after retries.", lastError)
    }

    private suspend fun download(item: AssetPackage, part: File, onProgress: (String, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val url = URL(item.url)
        require(url.protocol.equals("https", true)) { "Package URLs must use HTTPS." }
        part.parentFile?.mkdirs()
        if (part.length() > item.sizeBytes) part.delete()
        if (part.length() == item.sizeBytes) return@withContext
        val storage = StatFs(root.absolutePath)
        require(storage.availableBytes >= item.sizeBytes - part.length()) { "There is not enough free storage to download ${item.fileName}." }
        var existing = part.length()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            require(connection.url.protocol.equals("https", true)) { "The package server redirected away from HTTPS." }
            if (code == 416 && existing == item.sizeBytes) return@withContext
            if (code !in 200..299) throw IllegalStateException("Download server returned HTTP $code.")
            val resume = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (resume) {
                val range = connection.getHeaderField("Content-Range")
                if (range == null || !range.startsWith("bytes $existing-")) {
                    part.delete()
                    throw IllegalStateException("The server returned an invalid resume range; retrying from the start.")
                }
            }
            if (!resume) existing = 0
            val mode = if (resume) true else false
            FileOutputStream(part, mode).use { fileOut ->
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var transferredThisAttempt = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        transferredThisAttempt += count
                        if (existing + transferredThisAttempt > item.sizeBytes) throw IllegalStateException("${item.fileName} exceeded its declared size.")
                        fileOut.write(buffer, 0, count)
                        onProgress(item.id, existing + transferredThisAttempt, item.sizeBytes)
                    }
                    fileOut.fd.sync()
                }
            }
            require(part.length() == item.sizeBytes) { "${item.fileName} is incomplete; the partial file is kept for resume." }
        } finally { connection.disconnect() }
    }

    private fun verify(file: File, item: AssetPackage) {
        require(file.length() == item.sizeBytes) { "${item.fileName} has the wrong file size." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
        require(actual.equals(item.sha256, true)) { "Integrity verification failed for ${item.fileName}." }
    }

    private suspend fun extractSafely(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile
        var totalBytes = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= maxEntries) { "The song archive contains too many entries." }
                val target = File(destination, entry.name).canonicalFile
                require(target.toPath().startsWith(canonicalRoot.toPath())) { "The song archive contains an unsafe path." }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= maxExpandedBytes) { "The expanded song archive exceeds the storage safety limit." }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun activateStaging() {
        val backup = File(root, "library.previous")
        backup.deleteRecursively()
        if (installed.exists() && !installed.renameTo(backup)) throw IllegalStateException("Could not preserve the current library during update.")
        if (!staging.renameTo(installed)) {
            if (backup.exists()) backup.renameTo(installed)
            throw IllegalStateException("Could not activate the downloaded library.")
        }
        backup.deleteRecursively()
    }

    private fun JSONObject.toPackage() = AssetPackage(
        id = getString("id"),
        fileName = getString("filename"),
        url = getString("url"),
        sizeBytes = getLong("sizeBytes"),
        sha256 = getString("sha256"),
        expandedSizeBytes = optLong("expandedSizeBytes", getLong("sizeBytes"))
    )

    private fun JSONObject.toLibraryManifest(): LibraryManifest {
        val packages = buildList {
            optJSONArray("soundfonts")?.let { fonts -> for (i in 0 until fonts.length()) add(fonts.getJSONObject(i).toPackage()) }
            optJSONArray("songPackages")?.let { songs -> for (i in 0 until songs.length()) add(songs.getJSONObject(i).toPackage()) }
        }
        val deletedFiles = buildList {
            optJSONArray("deletedFiles")?.let { entries -> for (i in 0 until entries.length()) add(entries.getString(i)) }
        }
        deletedFiles.forEach(::safeRelativePath)
        return LibraryManifest(getString("libraryVersion"), optString("minimumAppVersion", "0.1.0"), packages, deletedFiles)
    }

    private fun LibraryManifest.toJson(): JSONObject {
        fun AssetPackage.asJson() = JSONObject()
            .put("id", id).put("filename", fileName).put("url", url)
            .put("sizeBytes", sizeBytes).put("sha256", sha256).put("expandedSizeBytes", expandedSizeBytes)
        val fonts = packages.filterNot { it.fileName.endsWith(".zip", true) }
        val songs = packages.filter { it.fileName.endsWith(".zip", true) }
        return JSONObject().put("app", "JKTV Karaoke").put("libraryVersion", libraryVersion).put("minimumAppVersion", minimumAppVersion)
            .put("soundfonts", org.json.JSONArray(fonts.map { it.asJson() }))
            .put("songPackages", org.json.JSONArray(songs.map { it.asJson() }))
            .put("deletedFiles", org.json.JSONArray(deletedFiles))
    }

    private fun safeName(name: String): String {
        require(name.isNotBlank() && name == File(name).name && !name.contains("..")) { "The manifest contains an unsafe filename." }
        return name
    }

    private fun safeRelativePath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            "The manifest contains an unsafe song path."
        }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "The manifest contains an unsafe song path."
        }
        return normalized
    }

    private fun isVersionAtLeast(current: String, required: String): Boolean {
        val left = current.split('.').map { it.toIntOrNull() ?: 0 }
        val right = required.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return true
    }
}
