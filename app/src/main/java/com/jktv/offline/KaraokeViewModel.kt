package com.jktv.offline

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.os.StatFs
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
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
import com.jktv.offline.playback.FluidSynthMidiEngine
import com.jktv.offline.playback.PlaybackState
import com.jktv.offline.updates.GitHubAppUpdate
import com.jktv.offline.updates.GitHubReleaseChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class LibraryUiState(
    val songCount: Int = 0,
    val installed: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val songQuery: String = "",
    val songListHasMore: Boolean = false,
    val songListLoading: Boolean = false,
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
    val error: String? = null,
    val playback: PlaybackState = PlaybackState(),
    val currentSong: SongEntity? = null,
    val backgroundVideoUri: String? = null,
    val backgroundVideoName: String? = null,
    val backgroundVideos: List<BackgroundVideoOption> = emptyList(),
    val soundFonts: List<SoundFontOption> = emptyList(),
    val selectedSoundFontPath: String? = null,
    val settingsMessage: String? = null,
    val themeName: String = "Violet",
    val masterVolume: Float = 0.8f,
    val reverbMix: Float = 0.25f,
    val chorusMix: Float = 0.35f
)

data class SoundFontOption(val path: String, val name: String)
data class BackgroundVideoOption(val path: String, val name: String)

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: KaraokeDao = KaraokeDatabase.get(application).karaokeDao()
    private val installer = AssetInstaller(application)
    private val indexer = SongIndexer(application)
    private val releaseChecker = GitHubReleaseChecker(application, BuildConfig.GITHUB_REPOSITORY)
    private val midiEngine = FluidSynthMidiEngine()
    private val preferences = application.getSharedPreferences("jktv-player-settings", Context.MODE_PRIVATE)
    private var pendingManifest: Pair<String, com.jktv.offline.assets.LibraryManifest>? = null
    private var setupJob: Job? = null
    private var searchJob: Job? = null
    private var advancingQueue = false
    private var songQueryGeneration = 0

    var state by androidx.compose.runtime.mutableStateOf(LibraryUiState())
        private set

    init {
        refresh()
        viewModelScope.launch {
            midiEngine.state.collect { playback ->
                val previous = state.playback
                state = state.copy(playback = playback)
                val reachedEnd = previous.file != null && previous.file == playback.file &&
                    previous.positionMs < previous.durationMs && playback.durationMs > 0L &&
                    playback.positionMs >= playback.durationMs && !playback.playing
                if (reachedEnd && !advancingQueue) {
                    advancingQueue = true
                    viewModelScope.launch {
                        try { advanceQueue() } finally { advancingQueue = false }
                    }
                }
            }
        }
    }

    fun playSong(song: SongEntity) = viewModelScope.launch {
        state = state.copy(currentSong = null, error = null, playback = PlaybackState())
        withContext(Dispatchers.IO) { midiEngine.stop() }
        val app = getApplication<Application>()
        try {
            val soundFont = availableSoundFonts()
                .firstOrNull { it.path == state.selectedSoundFontPath }
                ?.let { File(it.path) }
                ?: preferences.getString(KEY_SOUND_FONT, null)?.let(::File)?.takeIf { it.isFile }
                ?: availableSoundFonts()
                    .sortedByDescending { it.name.contains("default-gm", ignoreCase = true) }
                    .firstOrNull()
                    ?.let { File(it.path) }
                ?: throw IllegalStateException("No SF2 SoundFont is listed in the installed library.")
            val songsRoot = File(app.filesDir, "karaoke/library/songs").canonicalFile
            val midiFile = File(songsRoot, song.relativePath).canonicalFile
            require(midiFile.path.startsWith(songsRoot.path + File.separator) && midiFile.isFile) {
                "This song file is not available in the installed library."
            }
            withContext(Dispatchers.IO) {
                midiEngine.setSoundFont(soundFont)
                midiEngine.load(midiFile)
            }
            state = state.copy(currentSong = song)
            midiEngine.play()
            withContext(Dispatchers.IO) { dao.markPlayed(song.id, System.currentTimeMillis()) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(error = error.message ?: "Could not start MIDI playback.")
        }
    }

    fun togglePlayback() {
        if (state.playback.file == null) return
        val shouldPause = state.playback.playing
        viewModelScope.launch(Dispatchers.IO) {
            if (shouldPause) midiEngine.pause() else midiEngine.play()
        }
    }

    fun seekPlayback(positionMs: Long) {
        viewModelScope.launch(Dispatchers.IO) { midiEngine.seek(positionMs) }
    }

    fun skipToNext() = viewModelScope.launch {
        if (advancingQueue) return@launch
        val hasNext = withContext(Dispatchers.IO) { dao.queue().isNotEmpty() }
        if (!hasNext) {
            state = state.copy(error = "There are no songs waiting in the queue.")
            return@launch
        }
        advancingQueue = true
        try {
            withContext(Dispatchers.IO) { midiEngine.stop() }
            state = state.copy(currentSong = null, playback = PlaybackState(), error = null)
            advanceQueue()
        } finally {
            advancingQueue = false
        }
    }

    fun setBackgroundVideo(uri: Uri) {
        val app = getApplication<Application>()
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected video"
        preferences.edit().putString(KEY_BACKGROUND_URI, uri.toString()).putString(KEY_BACKGROUND_NAME, name).apply()
        state = state.copy(backgroundVideoUri = uri.toString(), backgroundVideoName = name, settingsMessage = null)
    }

    fun clearBackgroundVideo() {
        preferences.edit().remove(KEY_BACKGROUND_URI).remove(KEY_BACKGROUND_NAME).apply()
        state = state.copy(backgroundVideoUri = null, backgroundVideoName = null, settingsMessage = "Background video cleared.")
    }

    fun selectDownloadedBackground(path: String) {
        val option = state.backgroundVideos.firstOrNull { it.path == path } ?: return
        val file = File(option.path)
        if (!file.isFile || !isBackgroundVideoPathAllowed(file)) {
            state = state.copy(settingsMessage = "That downloaded video is no longer available. Download the library again or choose another video.")
            refresh()
            return
        }
        val uri = Uri.fromFile(file).toString()
        preferences.edit().putString(KEY_BACKGROUND_URI, uri).putString(KEY_BACKGROUND_NAME, option.name).apply()
        state = state.copy(backgroundVideoUri = uri, backgroundVideoName = option.name, settingsMessage = "${option.name} selected.")
    }

    fun selectSoundFont(path: String) {
        val option = state.soundFonts.firstOrNull { it.path == path } ?: return
        val file = File(option.path)
        if (!file.isFile || !isSoundFontPathAllowed(file)) {
            state = state.copy(settingsMessage = "That SoundFont is no longer available. Import it again or choose another one.")
            refresh()
            return
        }
        preferences.edit().putString(KEY_SOUND_FONT, file.absolutePath).apply()
        state = state.copy(selectedSoundFontPath = file.absolutePath, settingsMessage = "${option.name} selected.")

        val currentFile = state.playback.file
        if (currentFile != null) {
            val position = state.playback.positionMs
            val resume = state.playback.playing
            midiEngine.pause()
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        midiEngine.setSoundFont(file)
                        midiEngine.load(currentFile)
                        midiEngine.seek(position)
                    }
                    if (resume) midiEngine.play()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    state = state.copy(settingsMessage = error.message ?: "Could not apply the selected SoundFont.")
                }
            }
        }
    }

    fun setSoundMix(master: Float, reverb: Float, chorus: Float) {
        val safeMaster = master.coerceIn(0f, 1f)
        val safeReverb = reverb.coerceIn(0f, 1f)
        val safeChorus = chorus.coerceIn(0f, 1f)
        preferences.edit()
            .putFloat(KEY_MASTER_VOLUME, safeMaster)
            .putFloat(KEY_REVERB_MIX, safeReverb)
            .putFloat(KEY_CHORUS_MIX, safeChorus)
            .apply()
        state = state.copy(masterVolume = safeMaster, reverbMix = safeReverb, chorusMix = safeChorus)
        runCatching { midiEngine.setMixer(safeMaster, safeReverb, safeChorus) }
            .onFailure { state = state.copy(settingsMessage = it.message ?: "Could not apply the sound mix.") }
    }

    fun selectTheme(name: String) {
        val safeName = name.takeIf { it in setOf("Violet", "Ocean", "Emerald") } ?: "Violet"
        preferences.edit().putString(KEY_THEME, safeName).apply()
        state = state.copy(themeName = safeName)
    }

    fun importSoundFont(uri: Uri) = viewModelScope.launch {
        state = state.copy(settingsMessage = "Importing SoundFont…")
        try {
            val app = getApplication<Application>()
            val imported = withContext(Dispatchers.IO) {
                val originalName = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "Imported-SoundFont.sf2" }
                val safeName = originalName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .let { if (it.endsWith(".sf2", true)) it else "$it.sf2" }
                val folder = File(app.filesDir, "karaoke/custom-soundfonts").apply { mkdirs() }
                var destination = File(folder, safeName)
                if (destination.exists()) destination = File(folder, "${UUID.randomUUID().toString().take(8)}-$safeName")
                val temporary = File(folder, "${UUID.randomUUID()}.part")
                try {
                    val input = app.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open the selected file.")
                    input.use { source -> FileOutputStream(temporary).use { target -> source.copyTo(target, 128 * 1024) } }
                    val header = ByteArray(12)
                    temporary.inputStream().use { if (it.read(header) != header.size) throw IllegalArgumentException("The selected file is too small to be an SF2 SoundFont.") }
                    require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "sfbk") {
                        "Select a valid .sf2 SoundFont file."
                    }
                    if (!temporary.renameTo(destination)) temporary.copyTo(destination, overwrite = false)
                } finally {
                    temporary.delete()
                }
                destination
            }
            val fonts = withContext(Dispatchers.IO) { availableSoundFonts() }
            state = state.copy(soundFonts = fonts, settingsMessage = "${imported.nameWithoutExtension} imported.")
            selectSoundFont(imported.absolutePath)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(settingsMessage = error.message ?: "Could not import the SoundFont.")
        }
    }

    override fun onCleared() {
        midiEngine.release()
        super.onCleared()
    }

    fun refresh() = viewModelScope.launch {
        val currentQuery = state.songQuery
        val queryGeneration = songQueryGeneration
        val count = withContext(Dispatchers.IO) { dao.songCount() }
        val songs = querySongPage(currentQuery, 0)
        val queue = withContext(Dispatchers.IO) { dao.queueRows() }
        val fonts = withContext(Dispatchers.IO) { availableSoundFonts() }
        val videos = withContext(Dispatchers.IO) { availableBackgroundVideos() }
        val savedFont = preferences.getString(KEY_SOUND_FONT, null)
        val selectedFont = fonts.firstOrNull { it.path == savedFont }
            ?: fonts.sortedByDescending { it.name.contains("default-gm", ignoreCase = true) }.firstOrNull()
        val savedBackgroundUri = preferences.getString(KEY_BACKGROUND_URI, null)
        val savedBackgroundIsAvailable = savedBackgroundUri?.let { value ->
            runCatching {
                val uri = Uri.parse(value)
                when (uri.scheme) {
                    "file" -> File(uri.path.orEmpty()).isFile
                    "content" -> true // Persisted document-picker permission is retained by Android.
                    else -> false
                }
            }.getOrDefault(false)
        } ?: false
        val fallbackBackground = videos.firstOrNull()
        val backgroundUri = if (savedBackgroundIsAvailable) savedBackgroundUri
            else fallbackBackground?.path?.let { Uri.fromFile(File(it)).toString() }
        if (!savedBackgroundIsAvailable && fallbackBackground != null) {
            preferences.edit()
                .putString(KEY_BACKGROUND_URI, backgroundUri)
                .putString(KEY_BACKGROUND_NAME, fallbackBackground.name)
                .apply()
        }
        val queryStillCurrent = queryGeneration == songQueryGeneration
        state = state.copy(
            songCount = count,
            installed = count > 0,
            songs = if (queryStillCurrent) songs else state.songs,
            songQuery = if (queryStillCurrent) currentQuery else state.songQuery,
            songListHasMore = if (queryStillCurrent) (if (currentQuery.isBlank()) count > songs.size else songs.size == 100) else state.songListHasMore,
            songListLoading = if (queryStillCurrent) false else state.songListLoading,
            queue = queue,
            backgroundVideos = videos,
            soundFonts = fonts,
            selectedSoundFontPath = selectedFont?.path,
            themeName = preferences.getString(KEY_THEME, "Violet") ?: "Violet",
            masterVolume = preferences.getFloat(KEY_MASTER_VOLUME, 0.8f),
            reverbMix = preferences.getFloat(KEY_REVERB_MIX, 0.25f),
            chorusMix = preferences.getFloat(KEY_CHORUS_MIX, 0.35f),
            backgroundVideoUri = backgroundUri,
            backgroundVideoName = if (savedBackgroundIsAvailable) preferences.getString(KEY_BACKGROUND_NAME, null)
                ?: backgroundUri?.substringAfterLast('/')?.substringBefore('?') else fallbackBackground?.name
        )
    }

    private fun availableSoundFonts(): List<SoundFontOption> {
        val app = getApplication<Application>()
        val folders = listOf(
            File(app.filesDir, "karaoke/library/soundfonts"),
            File(app.filesDir, "karaoke/custom-soundfonts")
        )
        return folders.flatMap { folder ->
            folder.listFiles { file -> file.isFile && file.extension.equals("sf2", true) }
                .orEmpty().map { file -> SoundFontOption(file.absolutePath, file.nameWithoutExtension) }
        }.distinctBy { runCatching { File(it.path).canonicalPath }.getOrDefault(it.path) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun availableBackgroundVideos(): List<BackgroundVideoOption> {
        val app = getApplication<Application>()
        val folder = File(app.filesDir, "karaoke/library/backgrounds")
        return folder.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            .orEmpty().map { file -> BackgroundVideoOption(file.absolutePath, file.nameWithoutExtension) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun isBackgroundVideoPathAllowed(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        val root = File(getApplication<Application>().filesDir, "karaoke/library/backgrounds").canonicalPath + File.separator
        return path.startsWith(root) && file.extension.equals("mp4", true)
    }

    private fun isSoundFontPathAllowed(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        val app = getApplication<Application>()
        val roots = listOf("karaoke/library/soundfonts", "karaoke/custom-soundfonts")
            .map { File(app.filesDir, it).canonicalPath + File.separator }
        return roots.any { path.startsWith(it) } && file.extension.equals("sf2", true)
    }

    private companion object {
        const val KEY_BACKGROUND_URI = "background_video_uri"
        const val KEY_BACKGROUND_NAME = "background_video_name"
        const val KEY_SOUND_FONT = "selected_soundfont_path"
        const val KEY_THEME = "appearance_theme"
        const val KEY_MASTER_VOLUME = "mixer_master_volume"
        const val KEY_REVERB_MIX = "mixer_reverb"
        const val KEY_CHORUS_MIX = "mixer_chorus"
    }

    fun search(query: String) {
        searchJob?.cancel()
        val generation = ++songQueryGeneration
        state = state.copy(songs = emptyList(), songQuery = query, songListHasMore = false, songListLoading = true)
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(140)
            val rows = querySongPage(query, 0)
            if (generation != songQueryGeneration) return@launch
            state = state.copy(songs = rows, songQuery = query, songListHasMore = rows.size == 100, songListLoading = false)
        }
    }

    fun loadMoreSongs() = viewModelScope.launch {
        if (state.songListLoading || !state.songListHasMore) return@launch
        val generation = songQueryGeneration
        val query = state.songQuery
        val offset = state.songs.size
        state = state.copy(songListLoading = true)
        val more = querySongPage(query, offset)
        if (generation != songQueryGeneration) return@launch
        val combined = (state.songs + more).distinctBy { it.id }
        state = state.copy(songs = combined, songListHasMore = more.size == 100, songListLoading = false)
    }

    private suspend fun querySongPage(query: String, offset: Int): List<SongEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) dao.page(100, offset)
        else {
            val match = query.trim().split(Regex("\\s+"))
                .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
                .filter { it.isNotBlank() }.take(8).joinToString(" AND ") { "$it*" }
            if (match.isBlank()) dao.searchFallback(query.trim(), offset = offset)
            else runCatching { dao.searchFts(match, offset = offset) }.getOrDefault(emptyList())
                .ifEmpty { dao.searchFallback(query.trim(), offset = offset) }
        }
    }

    private suspend fun advanceQueue() {
        val next = withContext(Dispatchers.IO) {
            repeat(30) {
                val queued = dao.queue().firstOrNull() ?: return@withContext null
                dao.removeQueueItem(queued.id)
                dao.reindexQueue()
                dao.songById(queued.songId)?.let { return@withContext it }
            }
            null
        }
        refresh()
        if (next != null) playSong(next)
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
            val update = releaseChecker.checkForAppUpdate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            val manifestUrl = BuildConfig.LIBRARY_MANIFEST_URL
            require(manifestUrl.isNotBlank()) { "Configure jktvLibraryManifestUrl with the HTTPS library-manifest.json URL before checking the library." }
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
            state = state.copy(updateCheckBusy = false, updateMessage = error.message ?: "Could not check for updates or download the library manifest.")
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
