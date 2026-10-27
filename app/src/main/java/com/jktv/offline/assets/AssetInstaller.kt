package com.jktv.offline.assets

import android.content.Context
import android.os.StatFs
import com.jktv.offline.BuildConfig
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

enum class AssetCategory {
    SOUNDFONT,
    SONG_PACKAGE,
    BACKGROUND_VIDEO,
    BACKGROUND_PHOTO,
    OTHER
}

data class AssetPackage(
    val id: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val expandedSizeBytes: Long = 0L,
    val isCritical: Boolean = true,
    val category: AssetCategory = AssetCategory.OTHER
)

data class LibraryManifest(
    val libraryVersion: String,
    val minimumAppVersion: String = "0.1.0",
    val packages: List<AssetPackage>,
    val deletedFiles: List<String> = emptyList()
)

data class LibraryDiff(
    val pendingPackages: List<AssetPackage>,
    val obsoletePackages: List<AssetPackage>,
    val deletedFiles: List<String>,
    val totalDownloadBytes: Long,
    val hasUpdates: Boolean,
    val newSongsCount: Int,
    val newVideosCount: Int,
    val newPhotosCount: Int,
    val newSoundfontsCount: Int
)

class AssetInstaller(private val baseDir: File) {
    constructor(context: Context) : this(context.filesDir)
    private val root = File(baseDir, "karaoke")
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
            setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", "JKTV-Offline-Karaoke")
        }
        try {
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "The asset manifest could not be downloaded ($responseCode)." }
            require(connection.url.protocol.equals("https", true)) { "The manifest server redirected away from HTTPS." }
            val manifestText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
            require(manifestText.length <= 4 * 1024 * 1024) { "The asset manifest is too large." }
            val json = JSONObject(manifestText)

            val deletedFiles = buildList {
                json.optJSONArray("deletedFiles")?.let { entries ->
                    for (i in 0 until entries.length()) add(entries.getString(i))
                }
            }
            deletedFiles.forEach(::safeRelativePath)
            val deletedNames = deletedFiles.map { File(it).name.lowercase(Locale.ROOT) }.toSet()

            val packages = buildList {
                val fonts = json.optJSONArray("soundfonts")
                if (fonts != null) {
                    for (i in 0 until fonts.length()) {
                        val item = fonts.getJSONObject(i).toPackage(
                            isCritical = (i == 0 || fonts.getJSONObject(i).optString("id") == "default-gm"),
                            category = AssetCategory.SOUNDFONT
                        ).resolveAgainst(url)
                        if (item.fileName.lowercase(Locale.ROOT) !in deletedNames) add(item)
                    }
                }
                val songs = json.optJSONArray("songPackages")
                if (songs != null) {
                    for (i in 0 until songs.length()) {
                        val item = songs.getJSONObject(i).toPackage(
                            isCritical = true,
                            category = AssetCategory.SONG_PACKAGE
                        ).resolveAgainst(url)
                        if (item.fileName.lowercase(Locale.ROOT) !in deletedNames) add(item)
                    }
                }
                val backgrounds = json.optJSONArray("backgroundVideos")
                if (backgrounds != null) {
                    for (i in 0 until backgrounds.length()) {
                        val item = backgrounds.getJSONObject(i).toPackage(
                            isCritical = false,
                            category = AssetCategory.BACKGROUND_VIDEO
                        ).resolveAgainst(url)
                        if (item.fileName.lowercase(Locale.ROOT) !in deletedNames) add(item)
                    }
                }
                val photos = json.optJSONArray("backgroundPhotos")
                if (photos != null) {
                    for (i in 0 until photos.length()) {
                        val item = photos.getJSONObject(i).toPackage(
                            isCritical = false,
                            category = AssetCategory.BACKGROUND_PHOTO
                        ).resolveAgainst(url)
                        if (item.fileName.lowercase(Locale.ROOT) !in deletedNames) add(item)
                    }
                }
            }

            require(packages.isNotEmpty()) { "The asset manifest has no downloadable packages." }
            require(packages.any { it.fileName.endsWith(".zip", true) } && packages.any { it.fileName.endsWith(".sf2", true) || it.fileName.endsWith(".sf3", true) }) {
                "The asset manifest must include a song archive and an SF2/SF3 SoundFont."
            }
            require(packages.map { it.id.lowercase(Locale.ROOT) }.distinct().size == packages.size) { "The asset manifest contains duplicate package ids." }
            require(packages.all { it.sizeBytes > 0 && it.sha256.matches(Regex("(?i)[a-f0-9]{64}")) }) { "The manifest has a missing package size or SHA-256 hash." }
            packages.forEach {
                safeName(it.fileName)
                safeName(it.id)
                require(
                    it.fileName.endsWith(".zip", true) ||
                    it.fileName.endsWith(".sf2", true) ||
                    it.fileName.endsWith(".sf3", true) ||
                    it.fileName.endsWith(".mp4", true) ||
                    it.fileName.endsWith(".jpg", true) ||
                    it.fileName.endsWith(".jpeg", true) ||
                    it.fileName.endsWith(".png", true) ||
                    it.fileName.endsWith(".webp", true)
                ) {
                    "Unsupported library asset: ${it.fileName}"
                }
                require(URL(it.url).protocol.equals("https", true)) { "Package URLs must use HTTPS." }
            }
            val minimum = json.optString("minimumAppVersion", "0.1.0")
            require(minimum.matches(Regex("^\\d+(?:\\.\\d+){1,3}$"))) { "The manifest minimum app version is invalid." }
            require(isVersionAtLeast(BuildConfig.VERSION_NAME, minimum)) { "This library requires app version $minimum or later." }

            LibraryManifest(json.getString("libraryVersion"), minimum, packages, deletedFiles)
        } finally { connection.disconnect() }
    }

    fun isInstalled(): Boolean {
        val manifest = File(installed, "manifest.json")
        val songs = File(installed, "songs")
        val fonts = File(installed, "soundfonts")
        return manifest.isFile && songs.isDirectory && fonts.isDirectory &&
                (songs.list()?.isNotEmpty() == true) && (fonts.list()?.isNotEmpty() == true)
    }

    fun installedLibraryManifest(): LibraryManifest? = File(installed, "manifest.json").takeIf { it.isFile }?.let {
        runCatching { JSONObject(it.readText(Charsets.UTF_8)).toLibraryManifest() }.getOrNull()
    }

    fun parseManifest(jsonText: String): LibraryManifest = JSONObject(jsonText).toLibraryManifest()

    /**
     * Returns whichever manifest is newer: the one published on the cloud, or the one bundled
     * inside the APK. Protects against a stale cloud manifest that still lists deleted files
     * (e.g. Drunk.mp4) and omits newly uploaded ones. Bundled package URLs are resolved
     * against the cloud manifest URL so downloads still come from R2.
     */
    fun newestManifest(remote: LibraryManifest, bundledJson: String?, manifestUrl: String): LibraryManifest {
        if (bundledJson.isNullOrBlank()) return remote
        val bundled = runCatching { parseManifest(bundledJson.removePrefix("\uFEFF")) }.getOrNull() ?: return remote
        val bundledIsNewer = bundled.libraryVersion != remote.libraryVersion &&
            isVersionAtLeast(bundled.libraryVersion, remote.libraryVersion)
        if (!bundledIsNewer) return remote
        val base = URL(manifestUrl)
        val deletedNames = bundled.deletedFiles.map { File(it).name.lowercase(Locale.ROOT) }.toSet()
        return bundled.copy(
            packages = bundled.packages
                .filter { it.fileName.lowercase(Locale.ROOT) !in deletedNames }
                .map { it.resolveAgainst(base) }
        )
    }

    fun calculateDiff(remote: LibraryManifest, local: LibraryManifest?): LibraryDiff {
        val localById = local?.packages?.associateBy { it.id }.orEmpty()
        val localBySha = local?.packages?.associateBy { it.sha256.lowercase(Locale.ROOT) }.orEmpty()

        val pending = remote.packages.filter { pkg ->
            val match = localById[pkg.id]
            val exists = if (match != null) {
                match.sha256.equals(pkg.sha256, ignoreCase = true) && isAssetLocallyPresent(pkg)
            } else {
                localBySha.containsKey(pkg.sha256.lowercase(Locale.ROOT)) && isAssetLocallyPresent(pkg)
            }
            !exists
        }

        val remoteIds = remote.packages.map { it.id }.toSet()
        val obsolete = local?.packages?.filter { it.id !in remoteIds }.orEmpty()

        var songs = 0
        var videos = 0
        var photos = 0
        var fonts = 0
        pending.forEach {
            when (it.category) {
                AssetCategory.SONG_PACKAGE -> songs++
                AssetCategory.BACKGROUND_VIDEO -> videos++
                AssetCategory.BACKGROUND_PHOTO -> photos++
                AssetCategory.SOUNDFONT -> fonts++
                else -> {}
            }
        }

        val totalBytes = pending.sumOf { it.sizeBytes }
        val hasUpdates = pending.isNotEmpty() || obsolete.isNotEmpty() || remote.deletedFiles.isNotEmpty()

        return LibraryDiff(
            pendingPackages = pending,
            obsoletePackages = obsolete,
            deletedFiles = remote.deletedFiles,
            totalDownloadBytes = totalBytes,
            hasUpdates = hasUpdates,
            newSongsCount = songs,
            newVideosCount = videos,
            newPhotosCount = photos,
            newSoundfontsCount = fonts
        )
    }

    fun isAssetLocallyPresent(pkg: AssetPackage): Boolean {
        return when (pkg.category) {
            AssetCategory.SONG_PACKAGE -> {
                val songsDir = File(installed, "songs")
                songsDir.isDirectory && (songsDir.list()?.isNotEmpty() == true)
            }
            AssetCategory.SOUNDFONT -> {
                val file = File(installed, "soundfonts/${safeName(pkg.fileName)}")
                file.isFile && file.length() == pkg.sizeBytes
            }
            AssetCategory.BACKGROUND_VIDEO, AssetCategory.BACKGROUND_PHOTO -> {
                val file = File(installed, "backgrounds/${safeName(pkg.fileName)}")
                file.isFile && file.length() == pkg.sizeBytes
            }
            else -> false
        }
    }

    fun requiredDownloadBytes(manifest: LibraryManifest): Long = missingPackages(manifest).sumOf { it.sizeBytes }

    fun cachedPackageProgress(manifest: LibraryManifest): Map<String, Long> = missingPackages(manifest)
        .mapNotNull { item -> cachedBytes(item).takeIf { it > 0L }?.let { item.id to it } }
        .toMap()

    fun requiredStorageBytes(manifest: LibraryManifest): Long {
        val missing = missingPackages(manifest)
        val stagingExpansion = missing.sumOf { item ->
            if (item.fileName.endsWith(".zip", true)) item.expandedSizeBytes else item.sizeBytes
        }
        val downloadBytes = missing.sumOf { item -> (item.sizeBytes - cachedBytes(item)).coerceAtLeast(0L) }
        val existingCopyBytes = if (installed.isDirectory) installed.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        return downloadBytes + stagingExpansion + (if (!installed.isDirectory) existingCopyBytes else 0L)
    }

    private fun missingPackages(manifest: LibraryManifest): List<AssetPackage> {
        val prior = installedLibraryManifest()
        return manifest.packages.filterNot { item ->
            prior?.packages?.any { it.id == item.id && it.sha256.equals(item.sha256, true) } == true && isAssetLocallyPresent(item)
        }
    }

    private fun cachedBytes(item: AssetPackage): Long {
        val part = File(downloads, safeName(item.id) + "-" + safeName(item.fileName) + ".part")
        return part.length().takeIf { it <= item.sizeBytes } ?: 0L
    }

    suspend fun install(
        manifest: LibraryManifest,
        packagesToProcess: List<AssetPackage>? = null,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }
    ): List<String> = withContext(Dispatchers.IO) {
        downloads.mkdirs()
        val skippedUnavailable = mutableListOf<String>()
        val priorManifest = installedLibraryManifest()
        val isFirstTime = !installed.isDirectory || priorManifest == null
        val targetRoot = if (isFirstTime) staging else installed

        if (isFirstTime) {
            staging.deleteRecursively()
            staging.mkdirs()
            File(staging, "songs").mkdirs()
            File(staging, "soundfonts").mkdirs()
            File(staging, "backgrounds").mkdirs()
        } else {
            File(installed, "songs").mkdirs()
            File(installed, "soundfonts").mkdirs()
            File(installed, "backgrounds").mkdirs()
        }

        val queue = packagesToProcess ?: manifest.packages

        try {
            for (item in queue) {
                if (!isFirstTime && priorManifest.packages.any { it.id == item.id && it.sha256.equals(item.sha256, true) } && isAssetLocallyPresent(item)) {
                    continue
                }

                val archive = File(downloads, safeName(item.id) + "-" + safeName(item.fileName) + ".part")
                val downloaded = downloadWithRetry(item, archive, onProgress)
                if (!downloaded) {
                    skippedUnavailable.add(item.fileName)
                    continue
                }

                try {
                    verify(archive, item)
                    when {
                        item.fileName.endsWith(".zip", true) -> {
                            val target = File(targetRoot, "songs")
                            target.mkdirs()
                            extractSafely(archive, target)
                        }
                        item.fileName.endsWith(".sf2", true) || item.fileName.endsWith(".sf3", true) -> {
                            val name = safeName(item.fileName)
                            if (item.fileName.endsWith(".sf2", true)) {
                                val header = ByteArray(12)
                                archive.inputStream().use { input -> require(input.read(header) == header.size) { "The SoundFont file is incomplete." } }
                                require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "sfbk") {
                                    "${item.fileName} is not a valid SF2 SoundFont."
                                }
                            }
                            val target = File(targetRoot, "soundfonts/$name")
                            target.parentFile?.mkdirs()
                            archive.copyTo(target, overwrite = true)
                        }
                        item.fileName.endsWith(".mp4", true) -> {
                            val name = safeName(item.fileName)
                            val header = ByteArray(12)
                            archive.inputStream().use { input ->
                                require(input.read(header) == header.size && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
                                    "${item.fileName} is not a valid MP4 video."
                                }
                            }
                            val target = File(targetRoot, "backgrounds/$name")
                            target.parentFile?.mkdirs()
                            archive.copyTo(target, overwrite = true)
                        }
                        item.fileName.endsWith(".jpg", true) || item.fileName.endsWith(".jpeg", true) ||
                        item.fileName.endsWith(".png", true) || item.fileName.endsWith(".webp", true) -> {
                            val name = safeName(item.fileName)
                            val target = File(targetRoot, "backgrounds/$name")
                            target.parentFile?.mkdirs()
                            archive.copyTo(target, overwrite = true)
                        }
                    }
                    archive.delete()
                } catch (error: Throwable) {
                    archive.delete()
                    if (item.isCritical) throw error
                    skippedUnavailable.add(item.fileName)
                }
            }

            // Cleanup obsolete packages that were deleted from manifest
            val currentPackageIds = manifest.packages.map { it.id }.toSet()
            priorManifest?.packages?.filter { it.id !in currentPackageIds }?.forEach { removed ->
                val oldTarget = assetDestination(targetRoot, removed)
                if (oldTarget != null && manifest.packages.none { assetDestination(targetRoot, it)?.absolutePath == oldTarget.absolutePath }) {
                    oldTarget.delete()
                }
            }

            // Cleanup explicitly deleted files
            manifest.deletedFiles.forEach { relative ->
                val safePath = safeRelativePath(relative)
                File(targetRoot, "songs/$safePath").delete()
                File(targetRoot, safePath).delete()
                File(targetRoot, "backgrounds/${File(safePath).name}").delete()
            }

            // Write active manifest
            val finalManifest = manifest.copy(
                packages = manifest.packages.filter { it.fileName !in skippedUnavailable }
            )
            File(targetRoot, "manifest.json").writeText(finalManifest.toJson().toString(), Charsets.UTF_8)

            if (isFirstTime) {
                activateStaging()
            }
            downloads.deleteRecursively()
            skippedUnavailable
        } catch (error: Throwable) {
            if (isFirstTime) staging.deleteRecursively()
            throw error
        }
    }

    private suspend fun downloadWithRetry(
        item: AssetPackage,
        part: File,
        onProgress: (String, Long, Long) -> Unit
    ): Boolean {
        var lastError: Throwable? = null
        repeat(5) { attempt ->
            try {
                val ok = download(item, part, onProgress)
                if (ok) {
                    onProgress(item.id, part.length(), item.sizeBytes)
                    return true
                } else {
                    return false
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                if (error is FileNotFoundException || error.message?.contains("404") == true) {
                    if (!item.isCritical) {
                        part.delete()
                        return false
                    }
                }
                if (attempt < 4) delay(1_000L shl attempt)
            }
        }
        if (!item.isCritical) {
            part.delete()
            return false
        }
        throw IllegalStateException("Could not download ${item.fileName} after retries.", lastError)
    }

    private suspend fun download(
        item: AssetPackage,
        part: File,
        onProgress: (String, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val url = URL(item.url)
        require(url.protocol.equals("https", true)) { "Package URLs must use HTTPS." }
        part.parentFile?.mkdirs()
        if (part.length() > item.sizeBytes) part.delete()
        if (part.length() == item.sizeBytes) return@withContext true
        val storage = StatFs(root.absolutePath)
        require(storage.availableBytes >= item.sizeBytes - part.length()) { "There is not enough free storage to download ${item.fileName}." }
        var existing = part.length()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "JKTV-Offline-Karaoke")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            require(connection.url.protocol.equals("https", true)) { "The package server redirected away from HTTPS." }
            if (code == 404) {
                if (!item.isCritical) return@withContext false
                throw FileNotFoundException("File not found on cloud server (HTTP 404): ${item.fileName}")
            }
            if (code == 416 && existing == item.sizeBytes) return@withContext true
            if (code !in 200..299) throw IllegalStateException("Download server returned HTTP $code for ${item.fileName}.")
            val resume = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (resume) {
                val range = connection.getHeaderField("Content-Range")
                if (range == null || !range.startsWith("bytes $existing-")) {
                    part.delete()
                    throw IllegalStateException("The server returned an invalid resume range for ${item.fileName}; retrying from start.")
                }
            }
            if (!resume) existing = 0
            val mode = resume
            FileOutputStream(part, mode).use { fileOut ->
                BufferedInputStream(connection.inputStream).use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var transferredThisAttempt = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        transferredThisAttempt += count
                        if (existing + transferredThisAttempt > item.sizeBytes) {
                            throw IllegalStateException("${item.fileName} exceeded its declared size.")
                        }
                        fileOut.write(buffer, 0, count)
                        onProgress(item.id, existing + transferredThisAttempt, item.sizeBytes)
                    }
                    fileOut.fd.sync()
                }
            }
            require(part.length() == item.sizeBytes) { "${item.fileName} is incomplete; keeping partial file for resume." }
            true
        } finally { connection.disconnect() }
    }

    private fun verify(file: File, item: AssetPackage) {
        require(file.length() == item.sizeBytes) { "${item.fileName} has the wrong file size (expected ${item.sizeBytes}, was ${file.length()})." }
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
                require(target.toPath().startsWith(canonicalRoot.toPath())) { "The song archive contains an unsafe path: ${entry.name}" }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= maxExpandedBytes) { "The expanded song archive exceeds storage safety limit." }
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
        if (installed.exists() && !installed.renameTo(backup)) throw IllegalStateException("Could not preserve current library during update.")
        if (!staging.renameTo(installed)) {
            if (backup.exists()) backup.renameTo(installed)
            throw IllegalStateException("Could not activate downloaded library.")
        }
        backup.deleteRecursively()
    }

    private fun AssetPackage.resolveAgainst(baseUrl: URL): AssetPackage {
        val raw = if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url
        } else {
            val base = baseUrl.toExternalForm().substringBeforeLast('/') + "/"
            base + url.removePrefix("/")
        }
        val safeUrl = encodeUrlIfNeeded(raw)
        return copy(url = safeUrl)
    }

    private fun encodeUrlIfNeeded(rawUrl: String): String {
        return try {
            val parsed = URL(rawUrl)
            val uri = java.net.URI(
                parsed.protocol,
                parsed.userInfo,
                parsed.host,
                parsed.port,
                parsed.path,
                parsed.query,
                parsed.ref
            )
            uri.toASCIIString()
        } catch (_: Throwable) {
            rawUrl.replace(" ", "%20").replace("'", "%27")
        }
    }

    private fun JSONObject.toPackage(
        isCritical: Boolean = true,
        category: AssetCategory = AssetCategory.OTHER
    ) = AssetPackage(
        id = getString("id"),
        fileName = getString("filename"),
        url = getString("url"),
        sizeBytes = getLong("sizeBytes"),
        sha256 = getString("sha256"),
        expandedSizeBytes = optLong("expandedSizeBytes", getLong("sizeBytes")),
        isCritical = isCritical,
        category = category
    )

    private fun JSONObject.toLibraryManifest(): LibraryManifest {
        val packages = buildList {
            optJSONArray("soundfonts")?.let { fonts ->
                for (i in 0 until fonts.length()) add(fonts.getJSONObject(i).toPackage(
                    isCritical = (i == 0 || fonts.getJSONObject(i).optString("id") == "default-gm"),
                    category = AssetCategory.SOUNDFONT
                ))
            }
            optJSONArray("songPackages")?.let { songs ->
                for (i in 0 until songs.length()) add(songs.getJSONObject(i).toPackage(
                    isCritical = true,
                    category = AssetCategory.SONG_PACKAGE
                ))
            }
            optJSONArray("backgroundVideos")?.let { videos ->
                for (i in 0 until videos.length()) add(videos.getJSONObject(i).toPackage(
                    isCritical = false,
                    category = AssetCategory.BACKGROUND_VIDEO
                ))
            }
            optJSONArray("backgroundPhotos")?.let { photos ->
                for (i in 0 until photos.length()) add(photos.getJSONObject(i).toPackage(
                    isCritical = false,
                    category = AssetCategory.BACKGROUND_PHOTO
                ))
            }
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
        val soundFonts = packages.filter { it.category == AssetCategory.SOUNDFONT || it.fileName.endsWith(".sf2", true) || it.fileName.endsWith(".sf3", true) }
        val songs = packages.filter { it.category == AssetCategory.SONG_PACKAGE || it.fileName.endsWith(".zip", true) }
        val backgrounds = packages.filter { it.category == AssetCategory.BACKGROUND_VIDEO || it.fileName.endsWith(".mp4", true) }
        val photos = packages.filter { it.category == AssetCategory.BACKGROUND_PHOTO || it.fileName.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp)$")) }
        return JSONObject().put("formatVersion", 1).put("app", "JKTV Karaoke").put("libraryVersion", libraryVersion).put("minimumAppVersion", minimumAppVersion)
            .put("soundfonts", org.json.JSONArray(soundFonts.map { it.asJson() }))
            .put("songPackages", org.json.JSONArray(songs.map { it.asJson() }))
            .put("backgroundVideos", org.json.JSONArray(backgrounds.map { it.asJson() }))
            .put("backgroundPhotos", org.json.JSONArray(photos.map { it.asJson() }))
            .put("deletedFiles", org.json.JSONArray(deletedFiles))
    }

    private fun assetDestination(base: File, item: AssetPackage): File? = when {
        item.fileName.endsWith(".zip", true) -> null
        item.fileName.endsWith(".sf2", true) || item.fileName.endsWith(".sf3", true) -> File(base, "soundfonts/${safeName(item.fileName)}")
        item.fileName.endsWith(".mp4", true) -> File(base, "backgrounds/${safeName(item.fileName)}")
        item.fileName.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp)$")) -> File(base, "backgrounds/${safeName(item.fileName)}")
        else -> null
    }

    private fun safeName(name: String): String {
        val clean = File(name).name
        require(clean.isNotBlank() && !clean.contains("..") && !clean.contains("/") && !clean.contains("\\")) {
            "The manifest contains an unsafe filename: $name"
        }
        return clean
    }

    private fun safeRelativePath(path: String): String {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            "The manifest contains an unsafe path: $path"
        }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "The manifest contains an unsafe path: $path"
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
