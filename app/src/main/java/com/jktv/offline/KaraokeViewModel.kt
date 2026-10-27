package com.jktv.offline

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.toArgb
import com.jktv.offline.assets.AssetInstaller
import com.jktv.offline.assets.LibraryDiff
import com.jktv.offline.assets.LibraryManifest
import com.jktv.offline.data.ArtistCountRow
import com.jktv.offline.data.KaraokeDao
import com.jktv.offline.data.KaraokeDatabase
import com.jktv.offline.data.PlaylistEntity
import com.jktv.offline.data.PlaylistSongCrossRef
import com.jktv.offline.data.PlaylistSummaryRow
import com.jktv.offline.data.QueueEntity
import com.jktv.offline.data.QueueRow
import com.jktv.offline.data.SingerEntity
import com.jktv.offline.data.SongEntity
import com.jktv.offline.data.SongIndexer
import com.jktv.offline.data.SongSearchEngine
import com.jktv.offline.data.SmartSearchResult
import com.jktv.offline.playback.AudioMixerConfig
import com.jktv.offline.playback.FluidSynthMidiEngine
import com.jktv.offline.playback.LatencyCalibration
import com.jktv.offline.playback.LyricLine
import com.jktv.offline.playback.LyricsSyncEngine
import com.jktv.offline.playback.PlaybackState
import com.jktv.offline.playback.ResolvedLyricsSync
import com.jktv.offline.playback.SyncConfidence
import com.jktv.offline.playback.SyncSourceType
import com.jktv.offline.remote.RemoteCommandHandler
import com.jktv.offline.remote.RemoteServerState
import com.jktv.offline.remote.RemoteStageStatus
import com.jktv.offline.remote.RemoteStageMedia
import com.jktv.offline.remote.WifiRemoteServer
import com.jktv.offline.scoring.FinalScoreCard
import com.jktv.offline.scoring.KaraokeScorer
import com.jktv.offline.scoring.LiveScoreState
import com.jktv.offline.scoring.VoiceAnnouncer
import com.jktv.offline.updates.GitHubAppUpdate
import com.jktv.offline.updates.GitHubReleaseChecker
import com.jktv.offline.updates.AutomaticUpdatePolicy
import com.jktv.offline.ui.resolveLyricPalette
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val songCount: Int = 0,
    val installed: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val favorites: List<SongEntity> = emptyList(),
    val recentlyPlayed: List<SongEntity> = emptyList(),
    val mostPlayed: List<SongEntity> = emptyList(),
    val topArtists: List<ArtistCountRow> = emptyList(),
    val playlists: List<PlaylistSummaryRow> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylistSongs: List<SongEntity> = emptyList(),
    val singers: List<SingerEntity> = emptyList(),
    val activeSingerName: String = "Stage Singer",
    val activeDuetPartner: String = "",
    val karaokeMode: String = "SOLO",
    val queue: List<QueueRow> = emptyList(),
    val searchQuery: String = "",
    val searchSuggestions: List<String> = emptyList(),
    val typoToleranceEnabled: Boolean = true,
    val hideDuplicatesEnabled: Boolean = false,
    val duplicateCountRemoved: Int = 0,
    val selectedLetterFilter: String? = null,
    val selectedArtistFilter: String? = null,
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
    val downloadTotalFiles: Int = 0,
    val downloadRemainingFiles: Int = 0,
    val isFirstLaunchSetupLocked: Boolean = false,
    val libraryCheckBusy: Boolean = false,
    val libraryCheckMessage: String? = null,
    val availableLibraryUpdates: LibraryDiff? = null,
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
    val currentQueueRow: QueueRow? = null,
    val keyTranspose: Int = 0,
    val tempoMultiplier: Float = 1.0f,
    val melodyEnabled: Boolean = true,
    val mixerConfig: AudioMixerConfig = AudioMixerConfig(),
    val autoPlayNextEnabled: Boolean = true,
    val voiceAnnouncerEnabled: Boolean = true,
    val liveScoringEnabled: Boolean = false,
    val liveScoreState: LiveScoreState = LiveScoreState(),
    val finalScoreCard: FinalScoreCard? = null,
    val remoteServerState: RemoteServerState = RemoteServerState(),
    val lyricsEnabled: Boolean = true,
    val stageGuestQrEnabled: Boolean = true,
    val stageGuestJoinUrl: String = "",
    val countdownEnabled: Boolean = true,
    val songInfoEnabled: Boolean = true,
    val singerInfoEnabled: Boolean = true,
    val lyricWipeEnabled: Boolean = true,
    val backgroundIsImage: Boolean = false,
    val lyricFontSizeScale: Float = 1.15f,
    val lyricPaletteName: String = "CLASSIC_KARAOKE",
    val visualizerStyle: String = "NEON_BARS",
    val twoLineKaraokeLayout: Boolean = true,
    val lyricFontFamily: String = "DEFAULT",
    val lyricFontWeight: String = "BLACK",
    val lyricLetterSpacing: Float = 0f,
    val lyricLineSpacing: Float = 14f,
    val lyricTextAlignment: String = "CENTER",
    val lyricStagePosition: String = "CENTER",
    val lyricOutlineThickness: Float = 1.5f,
    val lyricShadowIntensity: Float = 1.0f,
    val lyricDisplayMode: String = "TWO_LINE",
    val lyricPhraseGrouping: String = "NATURAL_FULL",
    val isSoundFontPreviewing: Boolean = false,
    val previewInstrument: String = "PIANO",
    val soundFontLoading: Boolean = false,
    val backgroundBrightness: Float = 1.0f,
    val lyricsScrimAlpha: Float = 0.40f,
    val visualizerSensitivity: Float = 1.0f,
    val visualizerSpeed: Float = 1.0f,
    val visualizerBrightness: Float = 1.0f,
    val visualizerIntensityPreset: String = "BALANCED",
    val visualizerLyricsSafeZone: Boolean = true,
    val visualizerGlowStrength: Float = 1.0f,
    val visualizerPerformanceMode: Boolean = false,
    val customActiveFillHex: String = "#00F0FF",
    val customActiveGlowHex: String = "#FFD600",
    val customUnsungFillHex: String = "#FFFFFF",
    val customNextLineFillHex: String = "#B5C7E8",
    val latencyCalibration: LatencyCalibration = LatencyCalibration(),
    val fullScreenStage: Boolean = false,
    val backgroundVideoUri: String? = null,
    val backgroundVideoName: String? = null,
    val backgroundVideos: List<BackgroundVideoOption> = emptyList(),
    val soundFonts: List<SoundFontOption> = emptyList(),
    val selectedSoundFontPath: String? = null,
    val designStyle: String = "JKTV_SIGNATURE",
    val colorStyle: String = "CYAN_BLUE",
    val customPrimaryColorHex: String = "#00F0FF",
    val customSecondaryColorHex: String = "#FF2A85",
    val settingsMessage: String? = null
)

data class SoundFontOption(val path: String, val name: String)
data class BackgroundVideoOption(val path: String, val name: String)

class KaraokeViewModel(application: Application) : AndroidViewModel(application), RemoteCommandHandler {
    private val dao: KaraokeDao = KaraokeDatabase.get(application).karaokeDao()
    private val installer = AssetInstaller(application)
    private val indexer = SongIndexer(application)
    private val releaseChecker = GitHubReleaseChecker(application, BuildConfig.GITHUB_REPOSITORY)
    private val automaticUpdatePolicy = AutomaticUpdatePolicy()
    private var automaticUpdateJob: Job? = null
    private var appForeground = false
    private val midiEngine = FluidSynthMidiEngine()
    @Volatile private var nativeAudioMuted = false
    @Volatile private var browserAudioMuted = false
    @Synchronized private fun updateHostMute() {
        val muted = nativeAudioMuted || browserAudioMuted
        midiEngine.setOutputMuted(muted)
        if (muted) announcer.stop()
    }
    private val browserAudio = com.jktv.offline.audio.BrowserStageAudio(
        muteHost = { browserAudioMuted = it; updateHostMute() },
        presentationLatency = { midiEngine.setAudioPresentationLatencyMs(it) })
    val castController = com.jktv.offline.cast.CastController(application,
        snapshot = { com.jktv.offline.cast.CastSnapshot(state, playbackState, midiEngine.activeSoundFont) },
        mutePhone = { nativeAudioMuted = it; updateHostMute() },
        beforeTvAudio = {
            browserAudio.revokeAll()
            while (browserAudio.status().optBoolean("active")) { kotlinx.coroutines.delay(50) }
        })
    private val scorer = KaraokeScorer(application)
    private val announcer = VoiceAnnouncer(application)
    private val remoteServer = WifiRemoteServer(this, application.assets)
    private val webStageSession = UUID.randomUUID().toString()
    private val preferences = application.getSharedPreferences("jktv-player-settings", Context.MODE_PRIVATE)
    private var pendingManifest: Pair<String, LibraryManifest>? = null
    private var setupJob: Job? = null
    private var searchJob: Job? = null
    private var lastHandledCompletedId: Long = 0L
    private var autoResumeOnReconnect = false
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch(Dispatchers.Main) {
                if (state.paused && (state.isFirstLaunchSetupLocked || autoResumeOnReconnect)) {
                    if (state.availableLibraryUpdates != null) {
                        downloadLibraryUpdates()
                    } else {
                        setup(state.checkedManifestUrl ?: BuildConfig.LIBRARY_MANIFEST_URL)
                    }
                }
            }
        }
    }

    var state by mutableStateOf(loadInitialPreferencesState())
        private set

    var playbackState by mutableStateOf(PlaybackState())
        private set

    private var cachedSoundFonts: List<SoundFontOption>? = null
    private var cachedBackgroundVideos: List<BackgroundVideoOption>? = null
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, SmartSearchResult>()

    fun invalidateMediaCaches() {
        cachedSoundFonts = null
        cachedBackgroundVideos = null
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }
        midiEngine.pcmSink = { revision, position, captured, tempo, playing, pcm ->
            browserAudio.pcm.offer(revision, position, captured, tempo, playing, pcm)
        }
        viewModelScope.launch { while (isActive) { browserAudio.tick(); delay(50) } }
        viewModelScope.launch {
            var keptAlive=false;var renewedAt=0L
            while(isActive){
                val active=playbackState.playing||remoteServer.state.value.running
                val now=android.os.SystemClock.elapsedRealtime()
                if(active&&(!keptAlive||now-renewedAt>60000)) {
                    keptAlive=runCatching {com.jktv.offline.playback.PlaybackKeepAliveService.start(getApplication());true}.getOrDefault(false)
                    renewedAt=now
                } else if(!active&&keptAlive){com.jktv.offline.playback.PlaybackKeepAliveService.stop(getApplication());keptAlive=false}
                delay(1000)
            }
        }
        midiEngine.setPhraseGrouping(state.lyricPhraseGrouping)
        midiEngine.setMixerConfig(state.mixerConfig)
        midiEngine.setMelodyEnabled(state.melodyEnabled)
        midiEngine.setLatencyOffsetMs(state.latencyCalibration.totalEffectiveOffsetMs)
        refresh()

        viewModelScope.launch {
            midiEngine.state.collect { playback ->
                playbackState = playback
                scorer.updatePlaybackContext(
                    positionMs = playback.positionMs,
                    transpose = state.keyTranspose,
                    playing = playback.playing
                )
                // Only update monolithic state on macro playback changes to keep UI at 60/120 FPS
                if (state.playback.playing != playback.playing ||
                    state.playback.file != playback.file ||
                    state.playback.completedId != playback.completedId ||
                    state.playback.lyricLines != playback.lyricLines) {
                    state = state.copy(playback = playback)
                }
                if (playback.completedId > lastHandledCompletedId && playback.completedId > 0L) {
                    lastHandledCompletedId = playback.completedId
                    onSongFinishedNaturally()
                }
            }
        }
        viewModelScope.launch {
            scorer.state.collect { scoreState ->
                state = state.copy(liveScoreState = scoreState)
            }
        }
        viewModelScope.launch {
            remoteServer.state.collect { remoteState ->
                state = state.copy(remoteServerState = remoteState)
            }
        }
    }

    private fun loadInitialPreferencesState(): LibraryUiState {
        val fontScale = preferences.getFloat(KEY_LYRIC_FONT_SCALE, 1.15f).coerceIn(0.75f, 1.85f)
        val palette = preferences.getString(KEY_LYRIC_PALETTE, "CLASSIC_KARAOKE") ?: "CLASSIC_KARAOKE"
        val visualizer = preferences.getString(KEY_VISUALIZER_STYLE, "NEON_BARS") ?: "NEON_BARS"
        val autoNext = preferences.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        val voiceAnnounce = preferences.getBoolean(KEY_VOICE_ANNOUNCER, true)
        val singer = preferences.getString(KEY_ACTIVE_SINGER, "Stage Singer") ?: "Stage Singer"
        val mode = preferences.getString(KEY_KARAOKE_MODE, "SOLO") ?: "SOLO"
        val typo = preferences.getBoolean(KEY_TYPO_TOLERANCE, true)
        val hideDups = preferences.getBoolean(KEY_HIDE_DUPLICATES, false)
        val twoLine = preferences.getBoolean(KEY_TWO_LINE_STAGE, true)
        val displayMode = preferences.getString(KEY_LYRIC_DISPLAY_MODE, "TWO_LINE") ?: "TWO_LINE"
        val fontFamily = preferences.getString(KEY_LYRIC_FONT_FAMILY, "DEFAULT") ?: "DEFAULT"
        val fontWeight = preferences.getString(KEY_LYRIC_FONT_WEIGHT, "BLACK") ?: "BLACK"
        val letterSpacing = preferences.getFloat(KEY_LYRIC_LETTER_SPACING, 0f).coerceIn(-2f, 8f)
        val lineSpacing = preferences.getFloat(KEY_LYRIC_LINE_SPACING, 14f).coerceIn(4f, 32f)
        val textAlignment = preferences.getString(KEY_LYRIC_TEXT_ALIGNMENT, "CENTER") ?: "CENTER"
        val stagePosition = preferences.getString(KEY_LYRIC_STAGE_POSITION, "CENTER") ?: "CENTER"
        val outlineThickness = preferences.getFloat(KEY_LYRIC_OUTLINE_THICKNESS, 1.5f).coerceIn(0f, 4f)
        val shadowIntensity = preferences.getFloat(KEY_LYRIC_SHADOW_INTENSITY, 1.0f).coerceIn(0f, 2f)
        val phraseGrouping = preferences.getString(KEY_LYRIC_PHRASE_GROUPING, "NATURAL_FULL") ?: "NATURAL_FULL"
        val bgBrightness = preferences.getFloat(KEY_BACKGROUND_BRIGHTNESS, 1.0f).coerceIn(0.1f, 1f)
        val scrimAlpha = preferences.getFloat(KEY_LYRICS_SCRIM_ALPHA, 0.40f).coerceIn(0f, 0.95f)
        val visSens = preferences.getFloat(KEY_VISUALIZER_SENSITIVITY, 1.0f).coerceIn(0.5f, 2.5f)
        val visSpeed = preferences.getFloat(KEY_VISUALIZER_SPEED, 1.0f).coerceIn(0.5f, 2.5f)
        val visBright = preferences.getFloat(KEY_VISUALIZER_BRIGHTNESS, 1.0f).coerceIn(0.1f, 1f)
        val visIntensity = preferences.getString(KEY_VISUALIZER_INTENSITY_PRESET, "BALANCED") ?: "BALANCED"
        val visSafeZone = preferences.getBoolean(KEY_VISUALIZER_SAFE_ZONE, true)
        val visGlow = preferences.getFloat(KEY_VISUALIZER_GLOW_STRENGTH, 1.0f).coerceIn(0.2f, 2.0f)
        val visPerfMode = preferences.getBoolean(KEY_VISUALIZER_PERF_MODE, false)
        val customActive = preferences.getString(KEY_CUSTOM_ACTIVE_FILL, "#00F0FF") ?: "#00F0FF"
        val customGlow = preferences.getString(KEY_CUSTOM_ACTIVE_GLOW, "#FFD600") ?: "#FFD600"
        val customUnsung = preferences.getString(KEY_CUSTOM_UNSUNG_FILL, "#FFFFFF") ?: "#FFFFFF"
        val customNext = preferences.getString(KEY_CUSTOM_NEXT_LINE_FILL, "#B5C7E8") ?: "#B5C7E8"
        val designStyle = preferences.getString(KEY_DESIGN_STYLE, "JKTV_SIGNATURE") ?: "JKTV_SIGNATURE"
        val colorStyle = preferences.getString(KEY_COLOR_STYLE, "CYAN_BLUE") ?: "CYAN_BLUE"
        val customPrimary = preferences.getString(KEY_CUSTOM_PRIMARY_COLOR, "#00F0FF") ?: "#00F0FF"
        val customSecondary = preferences.getString(KEY_CUSTOM_SECONDARY_COLOR, "#FF2A85") ?: "#FF2A85"

        val globalOffset = preferences.getLong(KEY_GLOBAL_LATENCY_OFFSET_MS, 0L).coerceIn(-1000L, 1000L)
        val btOffset = preferences.getLong(KEY_BLUETOOTH_LATENCY_OFFSET_MS, 180L).coerceIn(0L, 600L)
        val btAuto = preferences.getBoolean(KEY_BLUETOOTH_AUTO_COMP, true)
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val initialCalibration = LatencyCalibration(
            globalOffsetMs = globalOffset,
            perSongOffsetMs = 0L,
            bluetoothOffsetMs = btOffset,
            bluetoothAutoCompensation = btAuto,
            bluetoothOutputDetected = btActive
        )
        val initialSetupCompleted = preferences.getBoolean(KEY_INITIAL_SETUP_COMPLETED, false)
        return LibraryUiState(
            lyricsEnabled = preferences.getBoolean("stage_lyricsEnabled", true),
            stageGuestQrEnabled = preferences.getBoolean("stage_stageGuestQrEnabled", true),
            countdownEnabled = preferences.getBoolean("stage_countdownEnabled", true),
            songInfoEnabled = preferences.getBoolean("stage_songInfoEnabled", true),
            singerInfoEnabled = preferences.getBoolean("stage_singerInfoEnabled", true),
            lyricWipeEnabled = preferences.getBoolean("stage_lyricWipeEnabled", true),
            backgroundIsImage = preferences.getBoolean("stage_backgroundIsImage", false),
            lyricFontSizeScale = fontScale,
            lyricPaletteName = palette,
            visualizerStyle = visualizer,
            twoLineKaraokeLayout = twoLine,
            lyricDisplayMode = displayMode,
            lyricFontFamily = fontFamily,
            lyricFontWeight = fontWeight,
            lyricLetterSpacing = letterSpacing,
            lyricLineSpacing = lineSpacing,
            lyricTextAlignment = textAlignment,
            lyricStagePosition = stagePosition,
            lyricOutlineThickness = outlineThickness,
            lyricShadowIntensity = shadowIntensity,
            lyricPhraseGrouping = phraseGrouping,
            backgroundBrightness = bgBrightness,
            lyricsScrimAlpha = scrimAlpha,
            visualizerSensitivity = visSens,
            visualizerSpeed = visSpeed,
            visualizerBrightness = visBright,
            visualizerIntensityPreset = visIntensity,
            visualizerLyricsSafeZone = visSafeZone,
            visualizerGlowStrength = visGlow,
            visualizerPerformanceMode = visPerfMode,
            customActiveFillHex = customActive,
            customActiveGlowHex = customGlow,
            customUnsungFillHex = customUnsung,
            customNextLineFillHex = customNext,
            designStyle = designStyle,
            colorStyle = colorStyle,
            customPrimaryColorHex = customPrimary,
            customSecondaryColorHex = customSecondary,
            latencyCalibration = initialCalibration,
            autoPlayNextEnabled = autoNext,
            voiceAnnouncerEnabled = voiceAnnounce,
            activeSingerName = singer,
            karaokeMode = mode,
            typoToleranceEnabled = typo,
            hideDuplicatesEnabled = hideDups,
            isFirstLaunchSetupLocked = !initialSetupCompleted
        )
    }

    fun playSong(
        song: SongEntity,
        queueRow: QueueRow? = null,
        resetAdjustments: Boolean = queueRow == null
    ) = viewModelScope.launch {
        midiEngine.stop()
        val nextKey = queueRow?.keyOffset ?: if (resetAdjustments) 0 else state.keyTranspose
        val nextTempo = queueRow?.let { (it.tempoPercent / 100f).coerceIn(0.5f, 1.5f) }
            ?: if (resetAdjustments) 1.0f else state.tempoMultiplier
        val nextMode = queueRow?.mode ?: state.karaokeMode

        midiEngine.setTranspose(nextKey)
        midiEngine.setTempoMultiplier(nextTempo)
        midiEngine.setMelodyEnabled(if (nextMode == "PRACTICE") true else state.melodyEnabled)

        state = state.copy(
            currentSong = song,
            currentQueueRow = queueRow,
            keyTranspose = nextKey,
            tempoMultiplier = nextTempo,
            karaokeMode = nextMode,
            finalScoreCard = null,
            error = null,
            playback = PlaybackState(completedId = lastHandledCompletedId)
        )
        val app = getApplication<Application>()
        try {
            val (soundFont, midiFile, resolvedSync) = withContext(Dispatchers.IO) {
                val sf = availableSoundFonts()
                    .firstOrNull { it.path == state.selectedSoundFontPath }
                    ?.let { File(it.path) }
                    ?: preferences.getString(KEY_SOUND_FONT, null)?.let(::File)?.takeIf { it.isFile }
                    ?: availableSoundFonts()
                        .sortedByDescending { it.name.contains("default-gm", ignoreCase = true) }
                        .firstOrNull()
                        ?.let { File(it.path) }
                    ?: throw IllegalStateException("No SF2 SoundFont is listed in the installed library.")

                val mf = resolveSongFile(app, song.relativePath)
                require(mf != null && mf.isFile) {
                    "This song file is not available in the installed library."
                }
                midiEngine.setSoundFont(sf)
                midiEngine.load(mf)
                val sync = midiEngine.currentTimeline?.let { timeline ->
                    LyricsSyncEngine.resolveForSong(
                        context = app,
                        songId = song.id,
                        midiFile = mf,
                        parsedTimeline = timeline
                    )
                }
                Triple(sf, mf, sync)
            }
            if (resolvedSync != null) {
                midiEngine.applyResolvedLyrics(resolvedSync)
            }
            val btActive = LyricsSyncEngine.isBluetoothAudioActive(app)
            val perSongSaved = preferences.getLong("song_latency_offset_${song.id}", 0L).coerceIn(-2000L, 2000L)
            val resolvedPerSong = if (perSongSaved != 0L) perSongSaved else (resolvedSync?.perSongOffsetMs ?: 0L)
            val updatedCal = state.latencyCalibration.copy(
                perSongOffsetMs = resolvedPerSong,
                bluetoothOutputDetected = btActive
            )
            midiEngine.setLatencyOffsetMs(updatedCal.totalEffectiveOffsetMs)

            scorer.setSongTimeline(midiEngine.state.value.melodyNotes)
            if (state.liveScoringEnabled || nextMode == "PRACTICE") {
                scorer.start()
            }
            if (state.voiceAnnouncerEnabled && !midiEngine.isOutputMuted()) {
                val singerName = queueRow?.singer ?: state.activeSingerName
                val partner = queueRow?.secondSinger ?: state.activeDuetPartner
                announcer.announceNextSinger(singerName, partner, song.title, nextMode)
            }
            state = state.copy(
                currentSong = song,
                currentQueueRow = queueRow,
                latencyCalibration = updatedCal
            )
            midiEngine.play()
            withContext(Dispatchers.IO) {
                dao.markPlayed(song.id, System.currentTimeMillis())
                val recent = dao.recentlyPlayed(50)
                val popular = dao.mostPlayed(50)
                withContext(Dispatchers.Main) {
                    state = state.copy(recentlyPlayed = recent, mostPlayed = popular)
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(error = error.message ?: "Could not start MIDI playback.")
        }
    }

    private fun resolveSongFile(app: Application, relativePath: String): File? {
        val roots = listOf(
            File(app.filesDir, "karaoke/library/songs"),
            File(app.filesDir, "karaoke/custom-songs")
        )
        for (root in roots) {
            if (!root.exists()) continue
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: continue
            val candidate = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull() ?: continue
            if (candidate.path.startsWith(canonicalRoot.path + File.separator) && candidate.isFile) {
                return candidate
            }
        }
        return null
    }

    private fun onSongFinishedNaturally() {
        val finishedSong = state.currentSong
        val finishedQueueRow = state.currentQueueRow
        val singerName = finishedQueueRow?.singer ?: state.activeSingerName

        if (finishedSong != null) {
            val card = scorer.buildFinalScoreCard(finishedSong.title, singerName)
            state = state.copy(finalScoreCard = card)
            scorer.playScoreFanfare(card.totalScore)
            if (state.voiceAnnouncerEnabled && !midiEngine.isOutputMuted()) {
                announcer.announceScore(singerName, card.totalScore)
            }
            viewModelScope.launch(Dispatchers.IO) {
                dao.recordSingerScore(singerName, card.totalScore)
            }
        }

        if (state.autoPlayNextEnabled && state.queue.isNotEmpty()) {
            playNextFromQueue()
        } else {
            refreshMetadataBuckets()
        }
    }

    fun dismissScoreCard() {
        state = state.copy(finalScoreCard = null)
    }

    fun playNextFromQueue() = viewModelScope.launch {
        val nextItem = withContext(Dispatchers.IO) {
            val rows = dao.queueRows()
            val head = rows.firstOrNull() ?: return@withContext null
            dao.removeQueueItem(head.id)
            dao.reindexQueue()
            val song = dao.songById(head.songId) ?: return@withContext null
            head to song
        }
        refresh()
        if (nextItem != null) {
            playSong(nextItem.second, queueRow = nextItem.first, resetAdjustments = false)
        }
    }

    fun playQueueRowImmediately(row: QueueRow) = viewModelScope.launch {
        val song = withContext(Dispatchers.IO) {
            dao.removeQueueItem(row.id)
            dao.reindexQueue()
            dao.songById(row.songId)
        }
        refresh()
        if (song != null) {
            playSong(song, queueRow = row, resetAdjustments = false)
        }
    }

    fun restartCurrentSong() {
        if (state.playback.file == null) return
        scorer.resetSessionCounters()
        midiEngine.seek(0L)
        midiEngine.play()
    }

    fun skipInterlude() {
        val target = state.playback.interludeSkipTargetMs ?: return
        midiEngine.seek(target)
    }

    fun togglePlayback() {
        if (state.playback.file == null) {
            if (state.queue.isNotEmpty()) {
                playNextFromQueue()
            }
            return
        }
        if (state.playback.playing) midiEngine.pause() else midiEngine.play()
    }

    fun seekPlayback(positionMs: Long) = midiEngine.seek(positionMs)

    fun setKeyTranspose(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        state = state.copy(keyTranspose = clamped)
        midiEngine.setTranspose(clamped)
    }

    fun setTempoMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.5f, 1.5f)
        state = state.copy(tempoMultiplier = clamped)
        midiEngine.setTempoMultiplier(clamped)
    }

    fun setMelodyEnabled(enabled: Boolean) {
        state = state.copy(melodyEnabled = enabled)
        midiEngine.setMelodyEnabled(enabled)
    }

    fun updateMixerConfig(config: AudioMixerConfig) {
        state = state.copy(mixerConfig = config)
        midiEngine.setMixerConfig(config)
    }

    fun setKaraokeMode(mode: String) {
        val normalized = mode.uppercase(Locale.ROOT)
        preferences.edit().putString(KEY_KARAOKE_MODE, normalized).apply()
        state = state.copy(karaokeMode = normalized)
        if (normalized == "PRACTICE") {
            setMelodyEnabled(true)
            setLiveScoringEnabled(true)
        }
    }

    fun setActiveSingerName(name: String) {
        preferences.edit().putString(KEY_ACTIVE_SINGER, name).apply()
        state = state.copy(activeSingerName = name)
    }

    fun setActiveDuetPartner(name: String) {
        state = state.copy(activeDuetPartner = name)
    }

    fun setAutoPlayNextEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
        state = state.copy(autoPlayNextEnabled = enabled)
    }

    fun setVoiceAnnouncerEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VOICE_ANNOUNCER, enabled).apply()
        state = state.copy(voiceAnnouncerEnabled = enabled)
    }

    fun setLiveScoringEnabled(enabled: Boolean) {
        state = state.copy(liveScoringEnabled = enabled)
        if (enabled) {
            scorer.start()
        } else {
            scorer.stop()
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        if (granted && (state.liveScoringEnabled || state.karaokeMode == "PRACTICE")) {
            scorer.start()
        } else {
            state = state.copy(
                liveScoringEnabled = false,
                liveScoreState = state.liveScoreState.copy(permissionGranted = granted, active = false)
            )
        }
    }

    fun setFullScreenStage(fullScreen: Boolean) {
        state = state.copy(fullScreenStage = fullScreen)
    }

    fun setStageElement(name: String, enabled: Boolean) {
        state = when (name) {
            "lyricsEnabled" -> state.copy(lyricsEnabled = enabled)
            "stageGuestQrEnabled" -> state.copy(stageGuestQrEnabled = enabled)
            "countdownEnabled" -> state.copy(countdownEnabled = enabled)
            "songInfoEnabled" -> state.copy(songInfoEnabled = enabled)
            "singerInfoEnabled" -> state.copy(singerInfoEnabled = enabled)
            "lyricWipeEnabled" -> state.copy(lyricWipeEnabled = enabled)
            else -> return
        }
        preferences.edit().putBoolean("stage_$name", enabled).apply()
    }

    fun setLyricFontSizeScale(scale: Float) {
        val clamped = scale.coerceIn(0.75f, 1.85f)
        preferences.edit().putFloat(KEY_LYRIC_FONT_SCALE, clamped).apply()
        state = state.copy(lyricFontSizeScale = clamped)
    }

    fun setLyricPaletteName(palette: String) {
        preferences.edit().putString(KEY_LYRIC_PALETTE, palette).apply()
        state = state.copy(lyricPaletteName = palette)
    }

    fun setVisualizerStyle(style: String) {
        preferences.edit().putString(KEY_VISUALIZER_STYLE, style).apply()
        state = state.copy(visualizerStyle = style)
    }

    fun setTwoLineKaraokeLayout(enabled: Boolean) {
        val mode = if (enabled) "TWO_LINE" else "FOUR_LINE"
        preferences.edit().putBoolean(KEY_TWO_LINE_STAGE, enabled).putString(KEY_LYRIC_DISPLAY_MODE, mode).apply()
        state = state.copy(twoLineKaraokeLayout = enabled, lyricDisplayMode = mode)
    }

    fun setLyricFontFamily(family: String) {
        preferences.edit().putString(KEY_LYRIC_FONT_FAMILY, family).apply()
        state = state.copy(lyricFontFamily = family)
    }

    fun setLyricFontWeight(weight: String) {
        preferences.edit().putString(KEY_LYRIC_FONT_WEIGHT, weight).apply()
        state = state.copy(lyricFontWeight = weight)
    }

    fun setLyricLetterSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(-2f, 8f)
        preferences.edit().putFloat(KEY_LYRIC_LETTER_SPACING, clamped).apply()
        state = state.copy(lyricLetterSpacing = clamped)
    }

    fun setLyricLineSpacing(spacing: Float) {
        val clamped = spacing.coerceIn(4f, 32f)
        preferences.edit().putFloat(KEY_LYRIC_LINE_SPACING, clamped).apply()
        state = state.copy(lyricLineSpacing = clamped)
    }

    fun setLyricTextAlignment(alignment: String) {
        preferences.edit().putString(KEY_LYRIC_TEXT_ALIGNMENT, alignment).apply()
        state = state.copy(lyricTextAlignment = alignment)
    }

    fun setLyricStagePosition(position: String) {
        preferences.edit().putString(KEY_LYRIC_STAGE_POSITION, position).apply()
        state = state.copy(lyricStagePosition = position)
    }

    fun setLyricOutlineThickness(thickness: Float) {
        val clamped = thickness.coerceIn(0f, 4f)
        preferences.edit().putFloat(KEY_LYRIC_OUTLINE_THICKNESS, clamped).apply()
        state = state.copy(lyricOutlineThickness = clamped)
    }

    fun setLyricShadowIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 2f)
        preferences.edit().putFloat(KEY_LYRIC_SHADOW_INTENSITY, clamped).apply()
        state = state.copy(lyricShadowIntensity = clamped)
    }

    fun setLyricDisplayMode(mode: String) {
        preferences.edit().putString(KEY_LYRIC_DISPLAY_MODE, mode).apply()
        preferences.edit().putBoolean(KEY_TWO_LINE_STAGE, mode == "TWO_LINE").apply()
        state = state.copy(lyricDisplayMode = mode, twoLineKaraokeLayout = (mode == "TWO_LINE"))
    }

    fun setLyricPhraseGrouping(grouping: String) {
        preferences.edit().putString(KEY_LYRIC_PHRASE_GROUPING, grouping).apply()
        state = state.copy(lyricPhraseGrouping = grouping)
        midiEngine.setPhraseGrouping(grouping)
    }

    fun setBackgroundBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0.1f, 1.0f)
        preferences.edit().putFloat(KEY_BACKGROUND_BRIGHTNESS, clamped).apply()
        state = state.copy(backgroundBrightness = clamped)
    }

    fun setLyricsScrimAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.0f, 0.95f)
        preferences.edit().putFloat(KEY_LYRICS_SCRIM_ALPHA, clamped).apply()
        state = state.copy(lyricsScrimAlpha = clamped)
    }

    fun setVisualizerSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.5f, 2.5f)
        preferences.edit().putFloat(KEY_VISUALIZER_SENSITIVITY, clamped).apply()
        state = state.copy(visualizerSensitivity = clamped)
    }

    fun setVisualizerSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.5f)
        preferences.edit().putFloat(KEY_VISUALIZER_SPEED, clamped).apply()
        state = state.copy(visualizerSpeed = clamped)
    }

    fun setVisualizerBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0.1f, 1.0f)
        preferences.edit().putFloat(KEY_VISUALIZER_BRIGHTNESS, clamped).apply()
        state = state.copy(visualizerBrightness = clamped)
    }

    fun setVisualizerIntensityPreset(preset: String) {
        preferences.edit().putString(KEY_VISUALIZER_INTENSITY_PRESET, preset).apply()
        val (sens, speed, bright) = when (preset.uppercase()) {
            "SUBTLE" -> Triple(0.65f, 0.75f, 0.60f)
            "IMMERSIVE" -> Triple(1.40f, 1.25f, 1.00f)
            else -> Triple(1.00f, 1.00f, 0.85f)
        }
        preferences.edit()
            .putFloat(KEY_VISUALIZER_SENSITIVITY, sens)
            .putFloat(KEY_VISUALIZER_SPEED, speed)
            .putFloat(KEY_VISUALIZER_BRIGHTNESS, bright)
            .apply()
        state = state.copy(
            visualizerIntensityPreset = preset,
            visualizerSensitivity = sens,
            visualizerSpeed = speed,
            visualizerBrightness = bright
        )
    }

    fun setVisualizerLyricsSafeZone(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VISUALIZER_SAFE_ZONE, enabled).apply()
        state = state.copy(visualizerLyricsSafeZone = enabled)
    }

    fun setVisualizerGlowStrength(glow: Float) {
        val clamped = glow.coerceIn(0.2f, 2.0f)
        preferences.edit().putFloat(KEY_VISUALIZER_GLOW_STRENGTH, clamped).apply()
        state = state.copy(visualizerGlowStrength = clamped)
    }

    fun setVisualizerPerformanceMode(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VISUALIZER_PERF_MODE, enabled).apply()
        state = state.copy(visualizerPerformanceMode = enabled)
    }

    fun setCustomPaletteColors(activeFill: String, activeGlow: String, unsungFill: String, nextLine: String) {
        preferences.edit()
            .putString(KEY_CUSTOM_ACTIVE_FILL, activeFill)
            .putString(KEY_CUSTOM_ACTIVE_GLOW, activeGlow)
            .putString(KEY_CUSTOM_UNSUNG_FILL, unsungFill)
            .putString(KEY_CUSTOM_NEXT_LINE_FILL, nextLine)
            .apply()
        state = state.copy(
            customActiveFillHex = activeFill,
            customActiveGlowHex = activeGlow,
            customUnsungFillHex = unsungFill,
            customNextLineFillHex = nextLine
        )
    }

    fun resetLyricsTypographyDefaults() {
        setLyricFontSizeScale(1.15f)
        setLyricFontFamily("DEFAULT")
        setLyricFontWeight("BLACK")
        setLyricLetterSpacing(0f)
        setLyricLineSpacing(14f)
        setLyricTextAlignment("CENTER")
        setLyricStagePosition("CENTER")
        setLyricOutlineThickness(1.5f)
        setLyricShadowIntensity(1.0f)
        setLyricDisplayMode("TWO_LINE")
    }

    fun resetWipePalettesDefaults() {
        setLyricPaletteName("CLASSIC_KARAOKE")
        setCustomPaletteColors("#00F0FF", "#FFD600", "#FFFFFF", "#B5C7E8")
    }

    fun resetVisualizerDefaults() {
        setVisualizerStyle("AMBIENT_GLOW")
        setVisualizerIntensityPreset("BALANCED")
        setVisualizerLyricsSafeZone(true)
        setVisualizerGlowStrength(1.0f)
        setVisualizerPerformanceMode(false)
    }

    fun resetBackgroundVideoDefaults() {
        setBackgroundBrightness(1.0f)
        setLyricsScrimAlpha(0.40f)
    }

    fun setDesignStyle(style: String) {
        preferences.edit().putString(KEY_DESIGN_STYLE, style).apply()
        state = state.copy(designStyle = style)
    }

    fun setColorStyle(style: String) {
        preferences.edit().putString(KEY_COLOR_STYLE, style).apply()
        state = state.copy(colorStyle = style)
    }

    fun setCustomThemeColors(primaryHex: String, secondaryHex: String) {
        preferences.edit()
            .putString(KEY_CUSTOM_PRIMARY_COLOR, primaryHex)
            .putString(KEY_CUSTOM_SECONDARY_COLOR, secondaryHex)
            .apply()
        state = state.copy(
            customPrimaryColorHex = primaryHex,
            customSecondaryColorHex = secondaryHex
        )
    }

    fun resetThemeDefaults() {
        setDesignStyle("JKTV_SIGNATURE")
        setColorStyle("CYAN_BLUE")
        setCustomThemeColors("#00F0FF", "#FF2A85")
    }

    fun playSampleSoundFont(soundFontPath: String, instrument: String) = viewModelScope.launch {
        state = state.copy(
            isSoundFontPreviewing = true,
            previewInstrument = instrument,
            soundFontLoading = true
        )
        try {
            val file = File(soundFontPath)
            if (file.isFile) {
                state = state.copy(soundFontLoading = false)
                midiEngine.playInstrumentSample(instrument, file)
            } else {
                state = state.copy(isSoundFontPreviewing = false, soundFontLoading = false)
            }
        } catch (_: Throwable) {
            state = state.copy(isSoundFontPreviewing = false, soundFontLoading = false)
        }
    }

    fun stopSampleSoundFont() {
        midiEngine.stopInstrumentSample()
        state = state.copy(isSoundFontPreviewing = false, soundFontLoading = false)
    }

    fun updateGlobalLatencyOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-1000L, 1000L)
        preferences.edit().putLong(KEY_GLOBAL_LATENCY_OFFSET_MS, clamped).apply()
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val updated = state.latencyCalibration.copy(
            globalOffsetMs = clamped,
            bluetoothOutputDetected = btActive
        )
        state = state.copy(latencyCalibration = updated)
        midiEngine.setLatencyOffsetMs(updated.totalEffectiveOffsetMs)
    }

    fun updatePerSongLatencyOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-2000L, 2000L)
        state.currentSong?.let { song ->
            preferences.edit().putLong("song_latency_offset_${song.id}", clamped).apply()
        }
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val updated = state.latencyCalibration.copy(
            perSongOffsetMs = clamped,
            bluetoothOutputDetected = btActive
        )
        state = state.copy(latencyCalibration = updated)
        midiEngine.setLatencyOffsetMs(updated.totalEffectiveOffsetMs)
    }

    fun updateBluetoothLatencyOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(0L, 600L)
        preferences.edit().putLong(KEY_BLUETOOTH_LATENCY_OFFSET_MS, clamped).apply()
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val updated = state.latencyCalibration.copy(
            bluetoothOffsetMs = clamped,
            bluetoothOutputDetected = btActive
        )
        state = state.copy(latencyCalibration = updated)
        midiEngine.setLatencyOffsetMs(updated.totalEffectiveOffsetMs)
    }

    fun setBluetoothAutoCompensation(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BLUETOOTH_AUTO_COMP, enabled).apply()
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val updated = state.latencyCalibration.copy(
            bluetoothAutoCompensation = enabled,
            bluetoothOutputDetected = btActive
        )
        state = state.copy(latencyCalibration = updated)
        midiEngine.setLatencyOffsetMs(updated.totalEffectiveOffsetMs)
    }

    fun resetLatencyCalibration() {
        preferences.edit()
            .putLong(KEY_GLOBAL_LATENCY_OFFSET_MS, 0L)
            .putLong(KEY_BLUETOOTH_LATENCY_OFFSET_MS, 180L)
            .putBoolean(KEY_BLUETOOTH_AUTO_COMP, true)
            .apply()
        state.currentSong?.let { song ->
            preferences.edit().remove("song_latency_offset_${song.id}").apply()
        }
        val btActive = LyricsSyncEngine.isBluetoothAudioActive(getApplication())
        val updated = LatencyCalibration(
            globalOffsetMs = 0L,
            perSongOffsetMs = 0L,
            bluetoothOffsetMs = 180L,
            bluetoothAutoCompensation = true,
            bluetoothOutputDetected = btActive
        )
        state = state.copy(latencyCalibration = updated)
        midiEngine.setLatencyOffsetMs(updated.totalEffectiveOffsetMs)
    }

    fun previewEditedLyrics(lines: List<LyricLine>) {
        val flat = lines.flatMap { line ->
            line.syllables.mapIndexed { idx, syl ->
                com.jktv.offline.playback.TimedLyricEvent(
                    timeMs = syl.startMs,
                    text = syl.text,
                    beginsLine = idx == 0,
                    endsWord = syl.endsWord
                )
            }
        }
        midiEngine.applyResolvedLyrics(
            ResolvedLyricsSync(
                lyricLines = lines,
                flatEvents = flat,
                sourceType = SyncSourceType.USER_SYNCED_SIDECAR,
                confidence = SyncConfidence.ACCURATE_SYLLABLE,
                perSongOffsetMs = state.latencyCalibration.perSongOffsetMs
            )
        )
    }

    fun saveCustomSyncedLyrics(lines: List<LyricLine>) = viewModelScope.launch {
        val song = state.currentSong ?: return@launch
        val app = getApplication<Application>()
        withContext(Dispatchers.IO) {
            LyricsSyncEngine.saveCustomSyncForSong(
                context = app,
                songId = song.id,
                lines = lines,
                perSongOffsetMs = state.latencyCalibration.perSongOffsetMs
            )
        }
        previewEditedLyrics(lines)
        state = state.copy(settingsMessage = "Saved custom syllable sync for \"${song.title}\".")
    }

    fun restoreOriginalSongLyrics() = viewModelScope.launch {
        val song = state.currentSong ?: return@launch
        val app = getApplication<Application>()
        val midiFile = resolveSongFile(app, song.relativePath) ?: return@launch
        val timeline = midiEngine.currentTimeline ?: return@launch
        val resolved = withContext(Dispatchers.IO) {
            LyricsSyncEngine.deleteCustomSyncForSong(app, song.id)
            LyricsSyncEngine.resolveForSong(
                context = app,
                songId = song.id,
                midiFile = midiFile,
                parsedTimeline = timeline
            )
        }
        midiEngine.applyResolvedLyrics(resolved)
        state = state.copy(settingsMessage = "Restored original ${resolved.sourceType.badgeText} timing for \"${song.title}\".")
    }

    fun importLyricsTimingFile(uri: Uri) = viewModelScope.launch {
        try {
            val app = getApplication<Application>()
            val content = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("Could not read timing file.")
            }
            val parsed: ResolvedLyricsSync? = if (content.trimStart().startsWith("{")) {
                runCatching { LyricsSyncEngine.parseJktvSyncJson(content) }.getOrNull()
            } else {
                val dur = state.playback.durationMs.coerceAtLeast(180_000L)
                runCatching { LyricsSyncEngine.parseLrc(content, dur, state.playback.melodyNotes) }.getOrNull()
            }
            require(parsed != null && parsed.lyricLines.isNotEmpty()) {
                "Selected file does not contain valid .LRC or .jktvsync.json lyric timestamps."
            }
            val song = state.currentSong
            if (song != null) {
                withContext(Dispatchers.IO) {
                    LyricsSyncEngine.saveCustomSyncForSong(
                        context = app,
                        songId = song.id,
                        lines = parsed.lyricLines,
                        perSongOffsetMs = parsed.perSongOffsetMs
                    )
                }
            }
            midiEngine.applyResolvedLyrics(parsed)
            if (parsed.perSongOffsetMs != 0L) {
                updatePerSongLatencyOffset(parsed.perSongOffsetMs)
            }
            state = state.copy(settingsMessage = "Imported ${parsed.lyricLines.size} synchronized lyric lines.")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(error = error.message ?: "Failed to import lyrics timing file.")
        }
    }

    fun exportLyricsToLrcUri(uri: Uri, lines: List<LyricLine>) = viewModelScope.launch {
        try {
            val app = getApplication<Application>()
            val song = state.currentSong
            val lrcText = LyricsSyncEngine.exportEnhancedLrc(
                songTitle = song?.title ?: "JKTV Karaoke Song",
                artist = song?.artist ?: "JKTV Offline",
                lines = lines,
                offsetMs = state.latencyCalibration.perSongOffsetMs
            )
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(lrcText.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not write to selected export location.")
            }
            state = state.copy(settingsMessage = "Exported enhanced syllable .LRC timing file.")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(error = error.message ?: "Failed to export .LRC file.")
        }
    }

    fun toggleWifiRemoteServer(enable: Boolean) {
        if (enable) {
            remoteServer.start()
        } else {
            remoteServer.stop()
        }
    }

    fun setBackgroundVideo(uri: Uri) {
        val app = getApplication<Application>()
        val name = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected video"
        val image = app.contentResolver.getType(uri)?.startsWith("image/") == true
        preferences.edit().putString(KEY_BACKGROUND_URI, uri.toString()).putString(KEY_BACKGROUND_NAME, name).putBoolean("stage_backgroundIsImage", image).apply()
        state = state.copy(backgroundVideoUri = uri.toString(), backgroundVideoName = name, backgroundIsImage = image, settingsMessage = null)
    }

    fun clearBackgroundVideo() {
        preferences.edit().remove(KEY_BACKGROUND_URI).remove(KEY_BACKGROUND_NAME).apply()
        state = state.copy(backgroundVideoUri = null, backgroundVideoName = null, settingsMessage = "Background video cleared (Neon Stage Visualizer active).")
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
        preferences.edit().putString(KEY_BACKGROUND_URI, uri).putString(KEY_BACKGROUND_NAME, option.name).putBoolean("stage_backgroundIsImage", false).apply()
        state = state.copy(backgroundVideoUri = uri, backgroundIsImage = false, backgroundVideoName = option.name, settingsMessage = "${option.name} selected.")
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
        } else {
            playSampleSoundFont(file.absolutePath, state.previewInstrument)
        }
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

    fun importCustomSong(uri: Uri) = viewModelScope.launch {
        state = state.copy(settingsMessage = "Importing custom MIDI/KAR song…")
        try {
            val app = getApplication<Application>()
            val importedSong = withContext(Dispatchers.IO) {
                val originalName = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty().ifBlank { "Custom-Song.mid" }
                val ext = originalName.substringAfterLast('.', "mid").lowercase(Locale.ROOT)
                require(ext in setOf("mid", "midi", "kar")) { "Select a valid .mid, .midi, or .kar karaoke file." }
                val safeName = originalName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val songsRoot = File(app.filesDir, "karaoke/library/songs").apply { mkdirs() }
                val customFolder = File(songsRoot, "custom").apply { mkdirs() }
                var destination = File(customFolder, safeName)
                if (destination.exists()) {
                    destination = File(customFolder, "${UUID.randomUUID().toString().take(6)}-$safeName")
                }
                app.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output, 64 * 1024) }
                } ?: throw IllegalStateException("Could not read selected song file.")

                val header = ByteArray(4)
                destination.inputStream().use {
                    if (it.read(header) != 4 || String(header, Charsets.US_ASCII) != "MThd") {
                        destination.delete()
                        throw IllegalArgumentException("Selected file does not have a valid Standard MIDI (MThd) header.")
                    }
                }
                val stem = destination.nameWithoutExtension.replace('_', ' ').trim()
                val parts = stem.split(Regex("\\s+[-–—]\\s+")).map(String::trim).filter(String::isNotEmpty)
                val title = parts.firstOrNull().orEmpty().ifBlank { stem }
                val artist = parts.drop(1).joinToString(" - ").ifBlank { "Custom Upload" }
                val nextNum = (90001 + (dao.songCount() % 9999)).toString()
                val rel = destination.relativeTo(songsRoot).invariantSeparatorsPath
                val entity = SongEntity(
                    songNumber = nextNum,
                    title = title.take(255),
                    artist = artist.take(190),
                    relativePath = rel,
                    packageVersion = "custom",
                    available = true
                )
                dao.putSongs(listOf(entity))
                entity
            }
            state = state.copy(settingsMessage = "Imported \"${importedSong.title}\" (#${importedSong.songNumber}).")
            refresh()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(settingsMessage = error.message ?: "Could not import MIDI/KAR file.")
        }
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        com.jktv.offline.playback.PlaybackKeepAliveService.stop(getApplication())
        remoteServer.release()
        castController.close()
        scorer.release()
        announcer.release()
        midiEngine.release()
        super.onCleared()
    }

    fun refresh() = viewModelScope.launch {
        val (count, searchRes, favorites, recent, popular, artists, playlists, singers, queue, fonts, videos) = withContext(Dispatchers.IO) {
            val countDef = async { dao.songCount() }
            val searchDef = async {
                SongSearchEngine.search(
                    dao = dao,
                    rawQuery = state.searchQuery,
                    typoTolerance = state.typoToleranceEnabled,
                    hideDuplicates = state.hideDuplicatesEnabled,
                    limit = 200
                )
            }
            val favoritesDef = async { dao.favorites() }
            val recentDef = async { dao.recentlyPlayed(50) }
            val popularDef = async { dao.mostPlayed(50) }
            val artistsDef = async { dao.topArtists(100) }
            val playlistsDef = async { dao.playlistSummaries() }
            val singersDef = async { dao.singers() }
            val queueDef = async { dao.queueRows() }
            val fontsDef = async { availableSoundFonts() }
            val videosDef = async { availableBackgroundVideos() }

            Tuples11(
                countDef.await(),
                searchDef.await(),
                favoritesDef.await(),
                recentDef.await(),
                popularDef.await(),
                artistsDef.await(),
                playlistsDef.await(),
                singersDef.await(),
                queueDef.await(),
                fontsDef.await(),
                videosDef.await()
            )
        }
        val savedFont = preferences.getString(KEY_SOUND_FONT, null)
        val selectedFont = fonts.firstOrNull { it.path == savedFont }
            ?: fonts.sortedByDescending { it.name.contains("default-gm", ignoreCase = true) }.firstOrNull()
        val backgroundUri = preferences.getString(KEY_BACKGROUND_URI, null)
        val selectedPlaylistId = state.selectedPlaylistId ?: playlists.firstOrNull()?.id
        val selectedPlaylistSongs = if (selectedPlaylistId != null) {
            withContext(Dispatchers.IO) { dao.playlistSongs(selectedPlaylistId) }
        } else emptyList()

        val priorCompleted = preferences.getBoolean(KEY_INITIAL_SETUP_COMPLETED, false)
        val isInstalled = count > 0
        if (isInstalled && !priorCompleted) {
            preferences.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETED, true).apply()
        }
        val isLocked = !priorCompleted && !isInstalled

        state = state.copy(
            songCount = count,
            installed = isInstalled,
            isFirstLaunchSetupLocked = isLocked,
            songs = searchRes.songs,
            searchSuggestions = searchRes.suggestions,
            duplicateCountRemoved = searchRes.duplicateCountRemoved,
            favorites = favorites,
            recentlyPlayed = recent,
            mostPlayed = popular,
            topArtists = artists,
            playlists = playlists,
            selectedPlaylistId = selectedPlaylistId,
            selectedPlaylistSongs = selectedPlaylistSongs,
            singers = singers,
            queue = queue,
            backgroundVideos = videos,
            soundFonts = fonts,
            selectedSoundFontPath = selectedFont?.path,
            backgroundVideoUri = backgroundUri,
            backgroundVideoName = preferences.getString(KEY_BACKGROUND_NAME, null)
                ?: backgroundUri?.substringAfterLast('/')?.substringBefore('?')
        )
    }

    private data class Tuples11<A, B, C, D, E, F, G, H, I, J, K>(
        val a: A, val b: B, val c: C, val d: D, val e: E,
        val f: F, val g: G, val h: H, val i: I, val j: J, val k: K
    )

    private fun refreshMetadataBuckets() = viewModelScope.launch {
        val (favorites, recent, popular, singers, queue) = withContext(Dispatchers.IO) {
            val fDef = async { dao.favorites() }
            val rDef = async { dao.recentlyPlayed(50) }
            val pDef = async { dao.mostPlayed(50) }
            val sDef = async { dao.singers() }
            val qDef = async { dao.queueRows() }
            Tuples5(fDef.await(), rDef.await(), pDef.await(), sDef.await(), qDef.await())
        }
        state = state.copy(
            favorites = favorites,
            recentlyPlayed = recent,
            mostPlayed = popular,
            singers = singers,
            queue = queue
        )
    }

    private data class Tuples5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    private fun availableSoundFonts(): List<SoundFontOption> {
        cachedSoundFonts?.let { return it }
        val app = getApplication<Application>()
        val folders = listOf(
            File(app.filesDir, "karaoke/library/soundfonts"),
            File(app.filesDir, "karaoke/custom-soundfonts")
        )
        val result = folders.flatMap { folder ->
            folder.listFiles { file -> file.isFile && file.extension.equals("sf2", true) }
                .orEmpty().map { file -> SoundFontOption(file.absolutePath, file.nameWithoutExtension) }
        }.distinctBy { runCatching { File(it.path).canonicalPath }.getOrDefault(it.path) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        cachedSoundFonts = result
        return result
    }

    private fun availableBackgroundVideos(): List<BackgroundVideoOption> {
        cachedBackgroundVideos?.let { return it }
        val app = getApplication<Application>()
        val folder = File(app.filesDir, "karaoke/library/backgrounds")
        val result = folder.listFiles { file -> file.isFile && file.extension.equals("mp4", true) }
            .orEmpty().map { file -> BackgroundVideoOption(file.absolutePath, file.nameWithoutExtension) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        cachedBackgroundVideos = result
        return result
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
        const val KEY_INITIAL_SETUP_COMPLETED = "jktv_initial_setup_completed"
        const val KEY_BACKGROUND_URI = "background_video_uri"
        const val KEY_BACKGROUND_NAME = "background_video_name"
        const val KEY_SOUND_FONT = "selected_soundfont_path"
        const val KEY_LYRIC_FONT_SCALE = "lyric_font_scale"
        const val KEY_LYRIC_PALETTE = "lyric_palette_name"
        const val KEY_VISUALIZER_STYLE = "visualizer_style"
        const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        const val KEY_VOICE_ANNOUNCER = "voice_announcer_enabled"
        const val KEY_ACTIVE_SINGER = "active_singer_name"
        const val KEY_KARAOKE_MODE = "karaoke_mode"
        const val KEY_TYPO_TOLERANCE = "typo_tolerance_enabled"
        const val KEY_HIDE_DUPLICATES = "hide_duplicates_enabled"
        const val KEY_TWO_LINE_STAGE = "two_line_karaoke_stage"
        const val KEY_GLOBAL_LATENCY_OFFSET_MS = "global_latency_offset_ms"
        const val KEY_BLUETOOTH_LATENCY_OFFSET_MS = "bluetooth_latency_offset_ms"
        const val KEY_BLUETOOTH_AUTO_COMP = "bluetooth_auto_compensation"
        const val KEY_LYRIC_FONT_FAMILY = "lyric_font_family"
        const val KEY_LYRIC_FONT_WEIGHT = "lyric_font_weight"
        const val KEY_LYRIC_LETTER_SPACING = "lyric_letter_spacing"
        const val KEY_LYRIC_LINE_SPACING = "lyric_line_spacing"
        const val KEY_LYRIC_TEXT_ALIGNMENT = "lyric_text_alignment"
        const val KEY_LYRIC_STAGE_POSITION = "lyric_stage_position"
        const val KEY_LYRIC_OUTLINE_THICKNESS = "lyric_outline_thickness"
        const val KEY_LYRIC_SHADOW_INTENSITY = "lyric_shadow_intensity"
        const val KEY_LYRIC_DISPLAY_MODE = "lyric_display_mode"
        const val KEY_LYRIC_PHRASE_GROUPING = "lyric_phrase_grouping"
        const val KEY_BACKGROUND_BRIGHTNESS = "background_brightness"
        const val KEY_LYRICS_SCRIM_ALPHA = "lyrics_scrim_alpha"
        const val KEY_VISUALIZER_SENSITIVITY = "visualizer_sensitivity"
        const val KEY_VISUALIZER_SPEED = "visualizer_speed"
        const val KEY_VISUALIZER_BRIGHTNESS = "visualizer_brightness"
        const val KEY_VISUALIZER_INTENSITY_PRESET = "visualizer_intensity_preset"
        const val KEY_VISUALIZER_SAFE_ZONE = "visualizer_safe_zone"
        const val KEY_VISUALIZER_GLOW_STRENGTH = "visualizer_glow_strength"
        const val KEY_VISUALIZER_PERF_MODE = "visualizer_perf_mode"
        const val KEY_CUSTOM_ACTIVE_FILL = "custom_active_fill"
        const val KEY_CUSTOM_ACTIVE_GLOW = "custom_active_glow"
        const val KEY_CUSTOM_UNSUNG_FILL = "custom_unsung_fill"
        const val KEY_CUSTOM_NEXT_LINE_FILL = "custom_next_line_fill"
        const val KEY_DESIGN_STYLE = "design_style"
        const val KEY_COLOR_STYLE = "color_style"
        const val KEY_CUSTOM_PRIMARY_COLOR = "custom_primary_color"
        const val KEY_CUSTOM_SECONDARY_COLOR = "custom_secondary_color"
    }

    fun search(query: String, immediate: Boolean = false) {
        val clean = query.trim()
        val cached = searchCache[clean.lowercase(Locale.ROOT)]
        if (cached != null) {
            state = state.copy(
                searchQuery = query,
                songs = cached.songs,
                searchSuggestions = cached.suggestions,
                duplicateCountRemoved = cached.duplicateCountRemoved,
                selectedLetterFilter = null,
                selectedArtistFilter = null
            )
            return
        }
        state = state.copy(
            searchQuery = query,
            selectedLetterFilter = null,
            selectedArtistFilter = null
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate && clean.isNotEmpty()) {
                delay(180L)
            }
            val result = withContext(Dispatchers.IO) {
                SongSearchEngine.search(
                    dao = dao,
                    rawQuery = clean,
                    typoTolerance = state.typoToleranceEnabled,
                    hideDuplicates = state.hideDuplicatesEnabled,
                    limit = 220
                )
            }
            if (searchCache.size > 64) searchCache.clear()
            searchCache[clean.lowercase(Locale.ROOT)] = result
            state = state.copy(
                songs = result.songs,
                searchSuggestions = result.suggestions,
                duplicateCountRemoved = result.duplicateCountRemoved
            )
        }
    }

    fun setTypoTolerance(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_TYPO_TOLERANCE, enabled).apply()
        state = state.copy(typoToleranceEnabled = enabled)
        searchCache.clear()
        search(state.searchQuery, immediate = true)
    }

    fun setHideDuplicates(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HIDE_DUPLICATES, enabled).apply()
        state = state.copy(hideDuplicatesEnabled = enabled)
        searchCache.clear()
        search(state.searchQuery, immediate = true)
    }

    fun filterByLetter(letter: String?) = viewModelScope.launch {
        state = state.copy(selectedLetterFilter = letter, selectedArtistFilter = null, searchQuery = "")
        val rows = withContext(Dispatchers.IO) {
            if (letter.isNullOrBlank()) dao.page(200, 0)
            else dao.songsByLetter(letter, 220, 0)
        }
        state = state.copy(songs = rows, searchSuggestions = emptyList())
    }

    fun filterByArtist(artist: String?) = viewModelScope.launch {
        state = state.copy(selectedArtistFilter = artist, selectedLetterFilter = null)
        val rows = withContext(Dispatchers.IO) {
            if (artist.isNullOrBlank()) dao.page(200, 0)
            else dao.songsByArtist(artist, 250)
        }
        state = state.copy(songs = rows)
    }

    fun playOrQueueByNumberCode(code: String, playNow: Boolean) = viewModelScope.launch {
        val clean = code.trim().removePrefix("#")
        if (clean.isEmpty()) return@launch
        val numericId = clean.toLongOrNull() ?: -1L
        val padded = clean.padStart(5, '0')
        val matched = withContext(Dispatchers.IO) {
            dao.searchByNumber(clean, padded, numericId, 5).firstOrNull()
        }
        if (matched != null) {
            if (playNow) {
                playSong(matched)
            } else {
                addToQueue(matched, state.activeSingerName, state.activeDuetPartner, state.karaokeMode)
            }
        } else {
            state = state.copy(error = "No song found with Songbook Number #$clean.")
        }
    }

    fun surpriseMeQueue(count: Int = 5) = viewModelScope.launch {
        val randoms = withContext(Dispatchers.IO) { dao.randomSongs(count) }
        for (song in randoms) {
            addToQueue(song, state.activeSingerName, state.activeDuetPartner, state.karaokeMode)
        }
    }

    fun setFavorite(song: SongEntity) {
        val newFav = !song.favorite
        val updatedSongs = state.songs.map { if (it.id == song.id) it.copy(favorite = newFav) else it }
        val updatedFavs = if (newFav) {
            listOf(song.copy(favorite = true)) + state.favorites.filter { it.id != song.id }
        } else {
            state.favorites.filter { it.id != song.id }
        }
        state = state.copy(
            songs = updatedSongs,
            favorites = updatedFavs,
            currentSong = state.currentSong?.let { if (it.id == song.id) it.copy(favorite = newFav) else it }
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.setFavorite(song.id, newFav)
        }
    }

    fun addToQueue(
        song: SongEntity,
        singer: String = state.activeSingerName,
        secondSinger: String = state.activeDuetPartner,
        mode: String = state.karaokeMode,
        keyOffset: Int = 0,
        tempoPercent: Int = 100
    ) = viewModelScope.launch {
        val resolvedSinger = singer.trim().ifBlank { nextRotationSingerName() }
        val newQueue = withContext(Dispatchers.IO) {
            val position = dao.queue().size
            dao.addQueueItem(
                QueueEntity(
                    songId = song.id,
                    singer = resolvedSinger,
                    position = position,
                    secondSinger = secondSinger.trim(),
                    mode = mode,
                    keyOffset = keyOffset,
                    tempoPercent = tempoPercent
                )
            )
            dao.insertSinger(SingerEntity(name = resolvedSinger, active = true))
            dao.queueRows()
        }
        state = state.copy(queue = newQueue)
        rotateActiveSingerAfterReservation(resolvedSinger)
    }

    private fun nextRotationSingerName(): String {
        val activeSingers = state.singers.filter { it.active }
        if (activeSingers.isEmpty()) return state.activeSingerName.ifBlank { "Stage Singer" }
        return activeSingers.first().name
    }

    private fun rotateActiveSingerAfterReservation(currentUsed: String) {
        val activeSingers = state.singers.filter { it.active }
        if (activeSingers.size >= 2) {
            val idx = activeSingers.indexOfFirst { it.name.equals(currentUsed, ignoreCase = true) }
            val nextIdx = if (idx >= 0) (idx + 1) % activeSingers.size else 0
            setActiveSingerName(activeSingers[nextIdx].name)
        }
    }

    fun removeFromQueue(id: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.removeQueueItem(id)
            dao.reindexQueue()
        }
        refreshMetadataBuckets()
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
        refreshMetadataBuckets()
    }

    fun moveQueueItemToTop(id: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val items = dao.queue().toMutableList()
            val from = items.indexOfFirst { it.id == id }
            if (from <= 0) return@withContext
            val item = items.removeAt(from)
            items.add(0, item)
            items.forEachIndexed { index, queued -> dao.setQueuePosition(queued.id, index) }
        }
        refreshMetadataBuckets()
    }

    fun clearQueue() = viewModelScope.launch {
        withContext(Dispatchers.IO) { dao.clearQueue() }
        refreshMetadataBuckets()
    }

    // Singer Rotation Management
    fun addSingerToRotation(name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isBlank()) return@launch
        withContext(Dispatchers.IO) {
            dao.insertSinger(SingerEntity(name = clean, active = true))
        }
        setActiveSingerName(clean)
        refreshMetadataBuckets()
    }

    fun toggleSingerActive(singer: SingerEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.setSingerActive(singer.id, !singer.active)
        }
        refreshMetadataBuckets()
    }

    fun removeSinger(singerId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.deleteSinger(singerId)
        }
        refreshMetadataBuckets()
    }

    // Custom Playlists Management
    fun createPlaylist(name: String, description: String = "") = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isBlank()) return@launch
        val id = withContext(Dispatchers.IO) {
            dao.insertPlaylist(PlaylistEntity(name = clean, description = description.trim()))
        }
        val summaries = withContext(Dispatchers.IO) { dao.playlistSummaries() }
        val resolvedId = if (id > 0L) id else summaries.firstOrNull { it.name.equals(clean, true) }?.id
        state = state.copy(playlists = summaries, selectedPlaylistId = resolvedId)
        if (resolvedId != null) selectPlaylist(resolvedId)
    }

    fun selectPlaylist(playlistId: Long) = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { dao.playlistSongs(playlistId) }
        state = state.copy(selectedPlaylistId = playlistId, selectedPlaylistSongs = songs)
    }

    fun deletePlaylist(playlistId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.clearPlaylistSongs(playlistId)
            dao.deletePlaylist(playlistId)
        }
        val summaries = withContext(Dispatchers.IO) { dao.playlistSummaries() }
        val nextId = summaries.firstOrNull()?.id
        val nextSongs = if (nextId != null) withContext(Dispatchers.IO) { dao.playlistSongs(nextId) } else emptyList()
        state = state.copy(playlists = summaries, selectedPlaylistId = nextId, selectedPlaylistSongs = nextSongs)
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId))
        }
        val summaries = withContext(Dispatchers.IO) { dao.playlistSummaries() }
        val songs = if (state.selectedPlaylistId == playlistId) {
            withContext(Dispatchers.IO) { dao.playlistSongs(playlistId) }
        } else state.selectedPlaylistSongs
        state = state.copy(playlists = summaries, selectedPlaylistSongs = songs)
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            dao.removeSongFromPlaylist(playlistId = playlistId, songId = songId)
        }
        val summaries = withContext(Dispatchers.IO) { dao.playlistSummaries() }
        val songs = if (state.selectedPlaylistId == playlistId) {
            withContext(Dispatchers.IO) { dao.playlistSongs(playlistId) }
        } else state.selectedPlaylistSongs
        state = state.copy(playlists = summaries, selectedPlaylistSongs = songs)
    }

    fun queueEntirePlaylist(playlistId: Long) = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { dao.playlistSongs(playlistId) }
        for (song in songs) {
            addToQueue(song)
        }
    }

    // RemoteCommandHandler implementation for Wi-Fi QR Remote
    override fun getStageStatus(): RemoteStageStatus {
        val cur = state.currentSong
        return RemoteStageStatus(
            currentTitle = cur?.title.orEmpty(),
            currentArtist = cur?.artist.orEmpty(),
            currentSinger = state.currentQueueRow?.singer ?: state.activeSingerName,
            playing = state.playback.playing,
            keyTranspose = state.keyTranspose,
            tempoPercent = (state.tempoMultiplier * 100f).roundToInt(),
            melodyEnabled = state.melodyEnabled,
            queue = state.queue
        )
    }

    override suspend fun getWebStageFrame(knownLinesRevision: String, knownSettingsRevision: String): org.json.JSONObject =
        withContext(Dispatchers.Main.immediate) {
            val audio = browserAudio.status()
            com.jktv.offline.stage.StageSnapshot.encode(state, midiEngine.state.value, webStageSession, knownLinesRevision, knownSettingsRevision)
                .put("audio", audio)
        }

    override suspend fun stageAudioControl(action: String, client: String, token: String, gesture: Boolean, latencyMs: Long): org.json.JSONObject =
        withContext(Dispatchers.Main.immediate) {
            when (action) {
                "claim" -> {
                    if (gesture) castController.setTvAudio(false)
                    else if (castController.status.value.audioOnTv)
                        return@withContext org.json.JSONObject().put("state", "revoked")
                    browserAudio.claim(client, gesture)
                }
                "commit" -> browserAudio.commit(client, token, !castController.status.value.audioOnTv, latencyMs)
                "release" -> browserAudio.release(client, token)
                else -> org.json.JSONObject().put("state", "invalid")
            }
        }

    override fun stageAudioRead(client: String, token: String, after: Long, latencyMs: Long) =
        browserAudio.read(client, token, after, latencyMs)

    override fun openWebStageMedia(): RemoteStageMedia? {
        val uriText = state.backgroundVideoUri ?: return null
        return runCatching {
            val parsed = Uri.parse(uriText)
            val uri = if (parsed.scheme == null) Uri.fromFile(File(uriText)) else parsed
            val afd = getApplication<Application>().contentResolver.openAssetFileDescriptor(uri, "r")
                ?: return null
            val size = afd.length
            if (size <= 0L) { afd.close(); return null }
            val input = object : java.io.FilterInputStream(afd.createInputStream()) {
                override fun close() { try { super.close() } finally { afd.close() } }
            }
            RemoteStageMedia(input, size, getApplication<Application>().contentResolver.getType(uri)
                ?: if (state.backgroundIsImage) "image/jpeg" else "video/mp4")
        }.getOrNull()
    }

    override suspend fun searchSongs(query: String): List<SongEntity> {
        return SongSearchEngine.search(
            dao = dao,
            rawQuery = query,
            typoTolerance = true,
            hideDuplicates = state.hideDuplicatesEnabled,
            limit = 60
        ).songs
    }

    override fun reserveSong(
        songId: Long,
        singer: String,
        secondSinger: String,
        mode: String,
        playNow: Boolean
    ) {
        viewModelScope.launch {
            val song = withContext(Dispatchers.IO) { dao.songById(songId) } ?: return@launch
            if (playNow) {
                playSong(song)
            } else {
                addToQueue(song, singer = singer, secondSinger = secondSinger, mode = mode)
            }
        }
    }

    override fun performAction(action: String) {
        viewModelScope.launch(Dispatchers.Main) {
            when (action.uppercase(Locale.ROOT)) {
                "PLAY_PAUSE" -> togglePlayback()
                "NEXT" -> playNextFromQueue()
                "RESTART" -> restartCurrentSong()
                "MELODY" -> setMelodyEnabled(!state.melodyEnabled)
                "KEY_DOWN" -> setKeyTranspose(state.keyTranspose - 1)
                "KEY_UP" -> setKeyTranspose(state.keyTranspose + 1)
                "TEMPO_DOWN" -> setTempoMultiplier(state.tempoMultiplier - 0.05f)
                "TEMPO_UP" -> setTempoMultiplier(state.tempoMultiplier + 0.05f)
            }
        }
    }

    fun checkForUpdates() = viewModelScope.launch {
        if (state.updateCheckBusy || state.updateDownloadBusy) return@launch
        automaticUpdatePolicy.attempted(SystemClock.elapsedRealtime())
        state = state.copy(
            updateCheckBusy = true,
            updateMessage = null,
            updateInfo = null,
            libraryUpdateManifest = null,
            libraryUpdateDownloadBytes = null,
            updateReadyPath = null
        )
        try {
            val update = releaseChecker.checkForAppUpdate(BuildConfig.VERSION_NAME)
            val ghManifestUrl = releaseChecker.findGitHubLibraryManifestUrl()
            val manifestUrl = ghManifestUrl ?: BuildConfig.LIBRARY_MANIFEST_URL
            val latestLibrary = if (manifestUrl.isNotBlank()) {
                runCatching { fetchLatestManifest(manifestUrl) }.getOrNull()
            } else null
            val installedVersion = installer.installedLibraryManifest()?.libraryVersion
            val libraryUpdate = if (latestLibrary != null &&
                (installedVersion == null || compareVersions(latestLibrary.libraryVersion, installedVersion) > 0)
            ) latestLibrary else null
            state = state.copy(
                updateCheckBusy = false,
                updateInfo = update,
                libraryUpdateManifest = libraryUpdate,
                libraryUpdateDownloadBytes = libraryUpdate?.let(installer::requiredDownloadBytes),
                updateMessage = if (update == null && libraryUpdate == null) "You're Up to Date! (v${BuildConfig.VERSION_NAME})" else null
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(
                updateCheckBusy = false,
                updateMessage = error.message ?: "Could not check for updates or download the library manifest."
            )
        }
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (!foreground) {
            automaticUpdateJob?.cancel()
            automaticUpdateJob = null
            return
        }
        if (automaticUpdateJob?.isActive == true) return
        automaticUpdateJob = viewModelScope.launch {
            while (isActive) {
                checkForUpdatesAutomatically()
                delay(60_000L)
            }
        }
    }

    private suspend fun checkForUpdatesAutomatically() {
        if (!appForeground || state.isFirstLaunchSetupLocked || state.busy || state.updateCheckBusy ||
            state.updateDownloadBusy || state.updateInfo != null || state.updateMessage != null ||
            state.libraryUpdateManifest != null || state.updateReadyPath != null ||
            !automaticUpdatePolicy.canCheck(SystemClock.elapsedRealtime())) return
        val network = connectivityManager?.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
        automaticUpdatePolicy.attempted(SystemClock.elapsedRealtime())
        state = state.copy(updateCheckBusy = true)
        try {
            val update = releaseChecker.checkForAppUpdate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            if (update != null && appForeground && automaticUpdatePolicy.shouldNotify(
                    AutomaticUpdatePolicy.identity(update),
                    preferences.getString("update_postponed_id", null),
                    preferences.getLong("update_postponed_at", 0L), System.currentTimeMillis()
                )) {
                state = state.copy(updateInfo = update, updateMessage = null, updateProgress = 0f)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                automaticUpdatePolicy.interrupted()
                throw error
            }
            automaticUpdatePolicy.failed(SystemClock.elapsedRealtime())
            // Automatic checks stay quiet offline or when GitHub is unavailable.
        } finally {
            state = state.copy(updateCheckBusy = false)
        }
    }

    fun dismissUpdateDialog() {
        state.updateInfo?.let { update ->
            preferences.edit().putString("update_postponed_id", AutomaticUpdatePolicy.identity(update))
                .putLong("update_postponed_at", System.currentTimeMillis()).apply()
        }
        state = state.copy(
            updateMessage = null,
            updateInfo = null,
            libraryUpdateManifest = null,
            libraryUpdateDownloadBytes = null,
            updateReadyPath = null,
            updateProgress = 0f
        )
    }

    fun downloadAndInstallUpdate() = viewModelScope.launch {
        if (state.updateDownloadBusy) return@launch
        val update = state.updateInfo ?: return@launch
        state = state.copy(
            updateDownloadBusy = true,
            updateProgress = 0f,
            updateMessage = "Downloading JKTV ${update.versionName} (Resumable HTTPS + SHA-256)…"
        )
        try {
            val file = releaseChecker.download(update) { bytes, total ->
                viewModelScope.launch(Dispatchers.Main) {
                    state = state.copy(updateProgress = if (total > 0L) bytes.toFloat() / total else 0f)
                }
            }
            state = state.copy(
                updateDownloadBusy = false,
                updateProgress = 1f,
                updateReadyPath = file.absolutePath,
                updateMessage = "APK verified (SHA-256). Tap Install Update to upgrade without losing songs or data."
            )
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
            val apk = File(filePath)
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

    fun inspectManifest(manifestUrl: String = BuildConfig.LIBRARY_MANIFEST_URL) = viewModelScope.launch {
        state = state.copy(busy = true, error = null, checkedManifestUrl = null, requiredBytes = null, progressText = "Checking asset manifest…")
        try {
            val resolvedUrl = manifestUrl.ifBlank { BuildConfig.LIBRARY_MANIFEST_URL }
            val url = checkedManifestUrl(resolvedUrl)
            val manifest = withContext(Dispatchers.IO) { fetchLatestManifest(url) }
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
        autoResumeOnReconnect = false
        setupJob?.cancel()
    }

    fun setup(manifestUrl: String = BuildConfig.LIBRARY_MANIFEST_URL) {
        if (setupJob?.isActive == true) return
        autoResumeOnReconnect = true
        setupJob = viewModelScope.launch {
            val resolvedUrl = manifestUrl.ifBlank { BuildConfig.LIBRARY_MANIFEST_URL }
            state = state.copy(
                busy = true,
                paused = false,
                downloadedBytes = 0L,
                downloadSpeedBytesPerSecond = 0L,
                remainingSeconds = null,
                error = null,
                progress = 0f,
                progressText = "Connecting to library server...",
                checkedManifestUrl = resolvedUrl
            )
            try {
                val url = checkedManifestUrl(resolvedUrl)
                val manifest = pendingManifest?.takeIf { it.first == url }?.second ?: withContext(Dispatchers.IO) {
                    fetchLatestManifest(url)
                }
                val totalBytes = installer.requiredDownloadBytes(manifest).coerceAtLeast(1L)
                val available = StatFs(getApplication<Application>().filesDir.absolutePath).availableBytes
                val requiredStorage = installer.requiredStorageBytes(manifest)
                if (available < requiredStorage) {
                    throw IllegalStateException("Only ${available / (1024 * 1024)} MB is free; setup needs about ${requiredStorage / (1024 * 1024)} MB for downloads and safe installation.")
                }
                val totalFiles = manifest.packages.size
                val completedPackages = mutableSetOf<String>()
                manifest.packages.forEach { pkg ->
                    if (installer.isAssetLocallyPresent(pkg)) {
                        completedPackages.add(pkg.id)
                    }
                }
                val completed = installer.cachedPackageProgress(manifest).toMutableMap()
                var lastTick = SystemClock.elapsedRealtime()
                var lastTransferred = completed.values.sum()
                state = state.copy(
                    downloadBytes = totalBytes,
                    requiredBytes = requiredStorage,
                    packageCount = totalFiles,
                    downloadTotalFiles = totalFiles,
                    downloadRemainingFiles = (totalFiles - completedPackages.size).coerceAtLeast(0),
                    downloadedBytes = lastTransferred,
                    progress = (lastTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
                )
                installer.install(manifest) { id, bytes, pkgTotal ->
                    completed[id] = bytes
                    if (bytes >= pkgTotal && pkgTotal > 0) {
                        completedPackages.add(id)
                    }
                    val transferred = completed.values.sum()
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = (now - lastTick).coerceAtLeast(1L)
                    val speed = ((transferred - lastTransferred).coerceAtLeast(0L) * 1000L / elapsed)
                    val remaining = if (speed > 0L) ((totalBytes - transferred).coerceAtLeast(0L) / speed) else null
                    if (elapsed >= 250L) {
                        lastTick = now
                        lastTransferred = transferred
                    }
                    viewModelScope.launch(Dispatchers.Main) {
                        val remainingFilesCount = (totalFiles - completedPackages.size).coerceAtLeast(0)
                        state = state.copy(
                            progress = (transferred.toFloat() / totalBytes).coerceIn(0f, 1f),
                            downloadedBytes = transferred,
                            downloadSpeedBytesPerSecond = speed,
                            remainingSeconds = remaining,
                            downloadRemainingFiles = remainingFilesCount,
                            progressText = "Downloading ${id.replace('-', ' ')}"
                        )
                    }
                }
                val indexed = indexer.index(manifest.libraryVersion) { current, count ->
                    viewModelScope.launch(Dispatchers.Main) {
                        state = state.copy(
                            progress = current.toFloat() / count.coerceAtLeast(1),
                            progressText = "Indexing songs: $current of $count",
                            downloadRemainingFiles = 0
                        )
                    }
                }
                if (indexed == 0) throw IllegalStateException("The downloaded packages did not contain any supported MIDI or KAR songs.")
                preferences.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETED, true).apply()
                invalidateMediaCaches()
                state = state.copy(
                    progress = 1f,
                    progressText = "Offline library ready ($indexed songs indexed)",
                    busy = false,
                    paused = false,
                    isFirstLaunchSetupLocked = false,
                    downloadSpeedBytesPerSecond = 0L,
                    remainingSeconds = 0L,
                    downloadRemainingFiles = 0
                )
                refresh()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    state = state.copy(
                        busy = false,
                        paused = true,
                        progressText = "Paused. Tap Resume Download to continue.",
                        downloadSpeedBytesPerSecond = 0L,
                        remainingSeconds = null
                    )
                    return@launch
                }
                state = state.copy(
                    busy = false,
                    paused = true,
                    error = error.message ?: "Library setup interrupted.",
                    progressText = "Download paused. Tap Resume to continue.",
                    downloadSpeedBytesPerSecond = 0L,
                    remainingSeconds = null
                )
            }
        }
    }

    fun checkForNewLibrary(manifestUrl: String = BuildConfig.LIBRARY_MANIFEST_URL) = viewModelScope.launch {
        if (state.libraryCheckBusy || state.busy) return@launch
        val resolvedUrl = manifestUrl.ifBlank { BuildConfig.LIBRARY_MANIFEST_URL }
        state = state.copy(
            libraryCheckBusy = true,
            libraryCheckMessage = "Checking cloud library for updates...",
            error = null
        )
        try {
            val url = checkedManifestUrl(resolvedUrl)
            val remoteManifest = withContext(Dispatchers.IO) {
                fetchLatestManifest(url)
            }
            val localManifest = withContext(Dispatchers.IO) {
                installer.installedLibraryManifest()
            }
            val diff = withContext(Dispatchers.IO) {
                installer.calculateDiff(remoteManifest, localManifest)
            }

            if (diff.hasUpdates) {
                val parts = mutableListOf<String>()
                if (diff.newSongsCount > 0) parts.add("${diff.newSongsCount} song package(s)")
                if (diff.newSoundfontsCount > 0) parts.add("${diff.newSoundfontsCount} SoundFont(s)")
                if (diff.newVideosCount > 0) parts.add("${diff.newVideosCount} video(s)")
                if (diff.newPhotosCount > 0) parts.add("${diff.newPhotosCount} photo(s)")
                if (diff.deletedFiles.isNotEmpty()) parts.add("${diff.deletedFiles.size} removed file(s)")
                val summary = if (parts.isNotEmpty()) parts.joinToString(", ") else "${diff.pendingPackages.size} update(s)"

                state = state.copy(
                    libraryCheckBusy = false,
                    availableLibraryUpdates = diff,
                    libraryUpdateManifest = remoteManifest,
                    downloadBytes = diff.totalDownloadBytes,
                    downloadTotalFiles = diff.pendingPackages.size,
                    downloadRemainingFiles = diff.pendingPackages.size,
                    libraryCheckMessage = "Updates available: $summary (${formatBytes(diff.totalDownloadBytes)})"
                )
            } else {
                state = state.copy(
                    libraryCheckBusy = false,
                    availableLibraryUpdates = null,
                    libraryUpdateManifest = null,
                    libraryCheckMessage = "Your JKTV Library is up to date."
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = state.copy(
                libraryCheckBusy = false,
                libraryCheckMessage = null,
                error = "Could not check cloud library: ${error.message ?: "Connection error"}"
            )
        }
    }

    fun downloadLibraryUpdates() {
        val diff = state.availableLibraryUpdates ?: return
        val manifest = state.libraryUpdateManifest ?: return
        if (setupJob?.isActive == true) return
        autoResumeOnReconnect = true
        setupJob = viewModelScope.launch {
            val totalBytes = diff.totalDownloadBytes.coerceAtLeast(1L)
            val totalFiles = diff.pendingPackages.size
            val completedPackages = mutableSetOf<String>()
            state = state.copy(
                busy = true,
                paused = false,
                error = null,
                progress = 0f,
                downloadBytes = totalBytes,
                downloadTotalFiles = totalFiles,
                downloadRemainingFiles = totalFiles,
                downloadedBytes = 0L,
                downloadSpeedBytesPerSecond = 0L,
                progressText = "Downloading updates..."
            )
            try {
                val progressMap = mutableMapOf<String, Long>()
                var lastTick = SystemClock.elapsedRealtime()
                var lastTransferred = 0L

                installer.install(manifest, packagesToProcess = diff.pendingPackages) { pkgId, bytes, pkgTotal ->
                    progressMap[pkgId] = bytes
                    if (bytes >= pkgTotal && pkgTotal > 0) {
                        completedPackages.add(pkgId)
                    }
                    val transferred = progressMap.values.sum()
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = (now - lastTick).coerceAtLeast(1L)
                    val speed = ((transferred - lastTransferred).coerceAtLeast(0L) * 1000L / elapsed)
                    val remainingSec = if (speed > 0L) ((totalBytes - transferred).coerceAtLeast(0L) / speed) else null
                    if (elapsed >= 250L) {
                        lastTick = now
                        lastTransferred = transferred
                    }
                    viewModelScope.launch(Dispatchers.Main) {
                        val remainingFilesCount = (totalFiles - completedPackages.size).coerceAtLeast(0)
                        state = state.copy(
                            progress = (transferred.toFloat() / totalBytes).coerceIn(0f, 1f),
                            downloadedBytes = transferred,
                            downloadSpeedBytesPerSecond = speed,
                            remainingSeconds = remainingSec,
                            downloadRemainingFiles = remainingFilesCount,
                            progressText = "Updating ${pkgId.replace('-', ' ')}"
                        )
                    }
                }

                if (diff.newSongsCount > 0 || diff.deletedFiles.any { it.contains("songs", ignoreCase = true) }) {
                    val indexed = indexer.index(manifest.libraryVersion) { current, count ->
                        viewModelScope.launch(Dispatchers.Main) {
                            state = state.copy(
                                progress = current.toFloat() / count.coerceAtLeast(1),
                                progressText = "Indexing songs: $current of $count",
                                downloadRemainingFiles = 0
                            )
                        }
                    }
                }

                invalidateMediaCaches()
                state = state.copy(
                    progress = 1f,
                    progressText = "Library updated successfully!",
                    busy = false,
                    paused = false,
                    availableLibraryUpdates = null,
                    libraryUpdateManifest = null,
                    libraryCheckMessage = "Your JKTV Library is up to date.",
                    downloadSpeedBytesPerSecond = 0L,
                    remainingSeconds = 0L,
                    downloadRemainingFiles = 0
                )
                refresh()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    state = state.copy(
                        busy = false,
                        paused = true,
                        progressText = "Update paused. Tap Resume to continue.",
                        downloadSpeedBytesPerSecond = 0L,
                        remainingSeconds = null
                    )
                    return@launch
                }
                state = state.copy(
                    busy = false,
                    paused = true,
                    error = error.message ?: "Library update interrupted.",
                    progressText = "Update paused. Tap Resume to continue.",
                    downloadSpeedBytesPerSecond = 0L,
                    remainingSeconds = null
                )
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val mb = safe / (1024.0 * 1024.0)
        return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
    }

    /** Reads the cloud manifest and falls back to the bundled one when it is newer than the cloud copy. */
    private suspend fun fetchLatestManifest(url: String): LibraryManifest {
        val remote = installer.readManifest(url)
        val bundled = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().assets.open("library-manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
        }
        return installer.newestManifest(remote, bundled, url)
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
