package com.jktv.offline

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.os.StatFs
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jktv.offline.assets.AssetInstaller
import com.jktv.offline.assets.LibraryManifest
import com.jktv.offline.data.KaraokeDatabase
import com.jktv.offline.data.KaraokeDao
import com.jktv.offline.data.QueueEntity
import com.jktv.offline.data.QueueRow
import com.jktv.offline.data.SongEntity
import com.jktv.offline.data.SongIndexer
import com.jktv.offline.updates.GitHubAppUpdate
import com.jktv.offline.updates.GitHubReleaseChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class LibraryUiState(
    val songCount: Int = 0,
    val installed: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val queue: List<QueueRow> = emptyList(),
    val progressText: String? = null,
    val progress: Float = 0f,
    val busy: Boolean = false,
    val paused: Boolean = false,
    val downloadedBytes: Long = 0L,
    val downloadSpeedBytesPerSecond: Long = 0L,
    val remainingSeconds: Long? = null,
    val checkedManifestUrl: String? = null,
    val downloadBytes: Long? = null,
    val requiredBytes: Long? = null,
    val availableBytes: Long? = null,
    val packageCount: Int = 0,
    val updateCheckBusy: Boolean = false,
    val updateDownloadBusy: Boolean = false,
    val updateMessage: String? = null,
    val updateInfo: GitHubAppUpdate? = null,
    val libraryUpdateManifest: LibraryManifest? = null,
    val libraryUpdateDownloadBytes: Long? = null,
    val updateReadyPath: String? = null,
    val updateProgress: Float = 0f,
    val error: String? = null
)

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: KaraokeDao = KaraokeDatabase.get(application).karaokeDao()
    private val installer = AssetInstaller(application)
    private val indexer = SongIndexer(application)
    private val releaseChecker = GitHubReleaseChecker(application, BuildConfig.GITHUB_REPOSITORY)
    private var pendingManifest: Pair<String, com.jktv.offline.assets.LibraryManifest>? = null
    private var setupJob: Job? = null

    var state by androidx.compose.runtime.mutableStateOf(LibraryUiState())
        private set

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val count = withContext(Dispatchers.IO) { dao.songCount() }
        val songs = withContext(Dispatchers.IO) { dao.page(50, 0) }
        val queue = withContext(Dispatchers.IO) { dao.queueRows() }
        state = state.copy(songCount = count, installed = count > 0, songs = songs, queue = queue)
    }

    fun search(query: String) = viewModelScope.launch {
        val rows = withContext(Dispatchers.IO) {
            if (query.isBlank()) dao.page(100, 0)
            else {
                val match = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
                    .filter { it.isNotBlank() }.take(8).joinToString(" AND ") { "$it*" }
                if (match.isBlank()) dao.searchFallback(query.trim())
                else runCatching { dao.searchFts(match) }.getOrDefault(emptyList()).ifEmpty { dao.searchFallback(query.trim()) }
            }
        }
        state = state.copy(songs = rows)
    }

    fun setFavorite(song: SongEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { dao.setFavorite(song.id, !song.favorite) }
        refresh()
    }

    fun addToQueue(song: SongEntity, singer: String = "Guest") = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val position = dao.queue().size
            dao.addQueueItem(QueueEntity(songId = song.id, singer = singer.ifBlank { "Guest" }, position = position))
        }
        refresh()
    }

    fun removeFromQueue(id: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.removeQueueItem(id)
            dao.reindexQueue()
        }
        refresh()
    }

    fun moveQueueItem(id: Long, direction: Int) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val items = dao.queue().toMutableList()
            val from = items.indexOfFirst { it.id == id }
            if (from < 0) return@withContext
            val to = (from + direction).coerceIn(0, items.lastIndex)
            if (to != from) {
                val item = items.removeAt(from)
                items.add(to, item)
                items.forEachIndexed { index, queued -> dao.setQueuePosition(queued.id, index) }
            }
        }
        refresh()
    }

    fun clearQueue() = viewModelScope.launch {
        withContext(Dispatchers.IO) { dao.clearQueue() }
        refresh()
    }

    fun checkForUpdates() = viewModelScope.launch {
        state = state.copy(updateCheckBusy = true, updateMessage = null, updateInfo = null, libraryUpdateManifest = null, libraryUpdateDownloadBytes = null, updateReadyPath = null)
        try {
            val update = releaseChecker.checkForAppUpdate(BuildConfig.VERSION_NAME)
            val manifestUrl = BuildConfig.GITHUB_LIBRARY_MANIFEST_URL
            require(manifestUrl.isNotBlank()) { "Set jktvGithubRepository to hillman3948-cyber/JKTV-Offline before checking the library manifest." }
            val latestLibrary = installer.readManifest(manifestUrl)
            val installedVersion = installer.installedLibraryManifest()?.libraryVersion
            val libraryUpdate = if (installedVersion == null || compareVersions(latestLibrary.libraryVersion, installedVersion) > 0) latestLibrary else null
            state = state.copy(
                updateCheckBusy = false,
                updateInfo = update,
                libraryUpdateManifest = libraryUpdate,
                libraryUpdateDownloadBytes = libraryUpdate?.let(installer::requiredDownloadBytes),
                updateMessage = if (update == null && libraryUpdate == null) "You're Up to Date!" else null
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(updateCheckBusy = false, updateMessage = error.message ?: "Could not check GitHub for updates.")
        }
    }

    fun dismissUpdateDialog() {
        state = state.copy(updateMessage = null, updateInfo = null, libraryUpdateManifest = null, libraryUpdateDownloadBytes = null, updateReadyPath = null, updateProgress = 0f)
    }

    fun downloadAndInstallUpdate() = viewModelScope.launch {
        val update = state.updateInfo ?: return@launch
        state = state.copy(updateDownloadBusy = true, updateProgress = 0f, updateMessage = "Downloading JKTV ${update.versionName}…")
        try {
            val file = releaseChecker.download(update) { bytes, total ->
                viewModelScope.launch(Dispatchers.Main) {
                    state = state.copy(updateProgress = if (total > 0L) bytes.toFloat() / total else 0f)
                }
            }
            state = state.copy(updateDownloadBusy = false, updateProgress = 1f, updateReadyPath = file.absolutePath, updateMessage = "APK verified. Continue to install the update.")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(updateDownloadBusy = false, updateMessage = error.message ?: "APK download failed.")
        }
    }

    fun installDownloadedUpdate() {
        val filePath = state.updateReadyPath ?: return
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            state = state.copy(updateMessage = "Allow JKTV to install this verified update, then return here and tap Install Update.")
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(settingsIntent)
            return
        }
        try {
            val apk = java.io.File(filePath)
            require(apk.isFile) { "The verified APK file is no longer available; check for updates again." }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        } catch (error: Throwable) {
            state = state.copy(updateMessage = error.message ?: "Could not open the Android package installer.")
        }
    }

    fun inspectManifest(manifestUrl: String) = viewModelScope.launch {
        state = state.copy(busy = true, error = null, checkedManifestUrl = null, requiredBytes = null, progressText = "Checking asset manifest…")
        try {
            val url = checkedManifestUrl(manifestUrl)
            val manifest = installer.readManifest(url)
            pendingManifest = url to manifest
            val available = StatFs(getApplication<Application>().filesDir.absolutePath).availableBytes
            state = state.copy(
                busy = false,
                checkedManifestUrl = url,
                downloadBytes = installer.requiredDownloadBytes(manifest),
                requiredBytes = installer.requiredStorageBytes(manifest),
                packageCount = manifest.packages.size,
                availableBytes = available,
                progressText = null
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(busy = false, error = error.message ?: "Could not check the asset manifest.", progressText = null)
        }
    }

    fun pauseSetup() {
        setupJob?.cancel()
    }

    fun setup(manifestUrl: String) {
        if (setupJob?.isActive == true) return
        setupJob = viewModelScope.launch {
        state = state.copy(busy = true, paused = false, downloadedBytes = 0L, downloadSpeedBytesPerSecond = 0L, remainingSeconds = null, error = null, progress = 0f, progressText = "Checking the asset manifest…")
        try {
            val url = checkedManifestUrl(manifestUrl)
            val manifest = pendingManifest?.takeIf { it.first == url }?.second ?: installer.readManifest(url)
            val total = installer.requiredDownloadBytes(manifest).coerceAtLeast(1L)
            val available = StatFs(getApplication<Application>().filesDir.absolutePath).availableBytes
            val requiredStorage = installer.requiredStorageBytes(manifest)
            if (available < requiredStorage) throw IllegalStateException("Only ${available / (1024 * 1024)} MB is free; setup needs about ${requiredStorage / (1024 * 1024)} MB for downloads and safe installation.")
            val completed = installer.cachedPackageProgress(manifest).toMutableMap()
            var lastTick = SystemClock.elapsedRealtime()
            var lastTransferred = completed.values.sum()
            state = state.copy(downloadedBytes = lastTransferred, progress = lastTransferred.toFloat() / total)
            installer.install(manifest) { id, bytes, size ->
                completed[id] = bytes
                val transferred = completed.values.sum()
                val now = SystemClock.elapsedRealtime()
                val elapsed = (now - lastTick).coerceAtLeast(1L)
                val speed = ((transferred - lastTransferred).coerceAtLeast(0L) * 1000L / elapsed)
                val remaining = if (speed > 0L) ((total - transferred).coerceAtLeast(0L) / speed) else null
                if (elapsed >= 250L) { lastTick = now; lastTransferred = transferred }
                viewModelScope.launch(Dispatchers.Main) {
                    state = state.copy(
                        progress = transferred.toFloat() / total,
                        downloadedBytes = transferred,
                        downloadSpeedBytesPerSecond = speed,
                        remainingSeconds = remaining,
                        progressText = "Downloading ${id.replace('-', ' ')}"
                    )
                }
            }
            val indexed = indexer.index(manifest.libraryVersion) { current, count ->
                viewModelScope.launch(Dispatchers.Main) {
                    state = state.copy(progress = current.toFloat() / count.coerceAtLeast(1), progressText = "Indexing songs: $current of $count")
                }
            }
            if (indexed == 0) throw IllegalStateException("The downloaded packages did not contain any supported MIDI or KAR songs.")
            state = state.copy(progress = 1f, progressText = "Offline library ready", busy = false, paused = false, downloadSpeedBytesPerSecond = 0L, remainingSeconds = 0L)
            refresh()
        } catch (error: Throwable) {
            if (error is CancellationException) {
                state = state.copy(busy = false, paused = true, progressText = "Paused. Tap Resume Download to continue.", downloadSpeedBytesPerSecond = 0L, remainingSeconds = null)
                return@launch
            }
            state = state.copy(busy = false, paused = false, error = error.message ?: "Library setup failed.", downloadSpeedBytesPerSecond = 0L, remainingSeconds = null)
        }
        }
    }

    private fun checkedManifestUrl(value: String): String {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
            "Enter a complete HTTPS manifest URL, such as https://github.com/hillman3948-cyber/JKTV-Offline/releases/latest/download/library-manifest.json."
        }
        return trimmed
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = Regex("\\d+").findAll(left).map { it.value.toLongOrNull() ?: 0L }.toList()
        val b = Regex("\\d+").findAll(right).map { it.value.toLongOrNull() ?: 0L }.toList()
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = a.getOrElse(index) { 0L }.compareTo(b.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        return left.compareTo(right, ignoreCase = true)
    }
}
