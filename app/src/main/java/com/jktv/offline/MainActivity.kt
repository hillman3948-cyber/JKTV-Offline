package com.jktv.offline
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.jktv.offline.data.QueueRow
import com.jktv.offline.data.SongEntity
import com.jktv.offline.playback.AudioMixerConfig
import com.jktv.offline.playback.LyricLine
import com.jktv.offline.playback.PlaybackState
import com.jktv.offline.ui.AppThemeStyles
import com.jktv.offline.ui.AudioMixerDialog
import com.jktv.offline.ui.LatencyCalibrationDialog
import com.jktv.offline.ui.LivePitchScoringBar
import com.jktv.offline.ui.LiveLyricsPreviewBox
import com.jktv.offline.ui.LyricPalettes
import com.jktv.offline.ui.LyricsSyncEditorDialog
import com.jktv.offline.ui.NeonStageVisualizer
import com.jktv.offline.ui.STAGE_VISUALIZER_ENABLED
import com.jktv.offline.ui.ScoreCardDialog
import com.jktv.offline.ui.SongNumberKeypadDialog
import com.jktv.offline.ui.SyllableKaraokeStage
import com.jktv.offline.ui.VisualizerStyles
import com.jktv.offline.ui.WifiRemoteQrDialog
import com.jktv.offline.ui.resolveLyricPalette
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JktvApp() }
    }
    override fun onStart() {
        super.onStart()
        ViewModelProvider(this)[KaraokeViewModel::class.java].setAppForeground(true)
    }
    override fun onStop() {
        ViewModelProvider(this)[KaraokeViewModel::class.java].setAppForeground(false)
        super.onStop()
    }
}

private enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SONGS("Library", Icons.Default.LibraryMusic),
    FAVORITES("Playlists", Icons.Default.Favorite),
    QUEUE("Queue", Icons.AutoMirrored.Filled.QueueMusic),
    PLAYING("Stage", Icons.Default.Album),
    SETTINGS("Setup", Icons.Default.Settings)
}

private enum class LibraryFilterTab(val label: String) {
    ALL("All Songs"),
    FAVORITES("Favorites"),
    RECENT("Recently Played"),
    POPULAR("Most Played"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists")
}

private val NeonColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF040914),
    primaryContainer = Color(0xFF0F2547),
    onPrimaryContainer = Color(0xFFD6F8FF),
    secondary = Color(0xFFFF2A85),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A1236),
    onSecondaryContainer = Color(0xFFFFD8EC),
    tertiary = Color(0xFFFFD600),
    background = Color(0xFF060913),
    onBackground = Color(0xFFF2F6FF),
    surface = Color(0xFF0C1224),
    onSurface = Color(0xFFF2F6FF),
    surfaceVariant = Color(0xFF141D38),
    onSurfaceVariant = Color(0xFF9FB2D9),
    outline = Color(0xFF283862)
)

@Composable
private fun JktvTheme(
    designStyle: String = "JKTV_SIGNATURE",
    colorStyle: String = "CYAN_BLUE",
    customPrimaryHex: String = "#00F0FF",
    customSecondaryHex: String = "#FF2A85",
    content: @Composable () -> Unit
) {
    val colorScheme = remember(designStyle, colorStyle, customPrimaryHex, customSecondaryHex) {
        AppThemeStyles.buildColorScheme(designStyle, colorStyle, customPrimaryHex, customSecondaryHex)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JktvApp(viewModel: KaraokeViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 700

    val library = viewModel.state
    val playback = viewModel.playbackState
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var libraryTab by rememberSaveable { mutableStateOf(LibraryFilterTab.ALL) }

    var showKeypadDialog by rememberSaveable { mutableStateOf(false) }
    var showQrRemoteDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioMixerDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSyncEditor by rememberSaveable { mutableStateOf(false) }
    var showLatencyCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    var pendingLrcExportLines by remember { mutableStateOf<List<LyricLine>?>(null) }
    var songForPlaylistDialog by remember { mutableStateOf<SongEntity?>(null) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.setBackgroundVideo(uri)
        }
    }
    val soundFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importSoundFont(uri)
    }
    val customSongPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importCustomSong(uri)
    }
    val lrcImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importLyricsTimingFile(uri)
    }
    val lrcExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        val lines = pendingLrcExportLines
        if (uri != null && lines != null) {
            viewModel.exportLyricsToLrcUri(uri, lines)
            pendingLrcExportLines = null
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }

    val isFullStage = screen == AppScreen.PLAYING && library.fullScreenStage

    val activity = context as? Activity
    DisposableEffect(isFullStage) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullStage) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    JktvTheme(
        designStyle = library.designStyle,
        colorStyle = library.colorStyle,
        customPrimaryHex = library.customPrimaryColorHex,
        customSecondaryHex = library.customSecondaryColorHex
    ) {
        if (library.isFirstLaunchSetupLocked) {
            FirstTimeSetupScreen(
                library = library,
                onStartSetup = { viewModel.setup() },
                onPauseSetup = { viewModel.pauseSetup() }
            )
        } else {
            Scaffold(
            contentWindowInsets = if (isFullStage) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
            topBar = {
                if (!isFullStage) {
                    FuturisticTopBar(
                        library = library,
                        castController = viewModel.castController,
                        onOpenKeypad = { showKeypadDialog = true },
                        onOpenRemoteQr = { showQrRemoteDialog = true },
                        onCheckUpdates = { viewModel.checkForUpdates() }
                    )
                }
            },
            bottomBar = {
                if (!isFullStage && !isWideScreen) {
                    Column {
                        if (library.currentSong != null && screen != AppScreen.PLAYING) {
                            MiniStageNowPlayingBar(
                                currentSong = library.currentSong,
                                isPlaying = playback.playing,
                                hasQueue = library.queue.isNotEmpty(),
                                onTogglePlay = { viewModel.togglePlayback() },
                                onNext = { viewModel.playNextFromQueue() },
                                onOpenStage = { screen = AppScreen.PLAYING }
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            val primaryNavScreens = listOf(
                                AppScreen.HOME,
                                AppScreen.SONGS,
                                AppScreen.QUEUE,
                                AppScreen.PLAYING,
                                AppScreen.SETTINGS
                            )
                            primaryNavScreens.forEach { target ->
                                val isSelected = (screen == target) || (target == AppScreen.SONGS && screen == AppScreen.FAVORITES)
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        if (target == AppScreen.SONGS && screen == AppScreen.FAVORITES) {
                                            libraryTab = LibraryFilterTab.ALL
                                        }
                                        screen = target
                                    },
                                    icon = { Icon(target.icon, contentDescription = target.label) },
                                    label = {
                                        Text(
                                            text = target.label,
                                            maxLines = 1,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF060913),
                                Color(0xFF0B1226),
                                Color(0xFF070A16)
                            )
                        )
                    )
                    .padding(if (isFullStage) PaddingValues(0.dp) else innerPadding)
            ) {
                if (!isFullStage && isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(8.dp))
                        val primaryNavScreens = listOf(
                            AppScreen.HOME,
                            AppScreen.SONGS,
                            AppScreen.QUEUE,
                            AppScreen.PLAYING,
                            AppScreen.SETTINGS
                        )
                        primaryNavScreens.forEach { target ->
                            val isSelected = (screen == target) || (target == AppScreen.SONGS && screen == AppScreen.FAVORITES)
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    if (target == AppScreen.SONGS && screen == AppScreen.FAVORITES) {
                                        libraryTab = LibraryFilterTab.ALL
                                    }
                                    screen = target
                                },
                                icon = { Icon(target.icon, contentDescription = target.label) },
                                label = { Text(target.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            library = library,
                            onSelectScreen = { target ->
                                if (target == AppScreen.FAVORITES) libraryTab = LibraryFilterTab.FAVORITES
                                if (target == AppScreen.SONGS) libraryTab = LibraryFilterTab.ALL
                                screen = target
                            },
                            onOpenKeypad = { showKeypadDialog = true },
                            onOpenRemoteQr = { showQrRemoteDialog = true },
                            onSurpriseMe = {
                                viewModel.surpriseMeQueue(5)
                                screen = AppScreen.QUEUE
                            },
                            onPlaySong = { song ->
                                viewModel.playSong(song)
                                screen = AppScreen.PLAYING
                            },
                            onQueueSong = { song -> viewModel.addToQueue(song) },
                            onFavoriteSong = { song -> viewModel.setFavorite(song) },
                            onSetMode = { mode ->
                                viewModel.setKaraokeMode(mode)
                                if (mode == "PRACTICE") {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                        AppScreen.SONGS -> SongsLibraryScreen(
                            library = library,
                            activeTab = libraryTab,
                            onSelectTab = { libraryTab = it },
                            onSearch = { viewModel.search(it) },
                            onToggleTypoTolerance = { viewModel.setTypoTolerance(!library.typoToleranceEnabled) },
                            onToggleHideDuplicates = { viewModel.setHideDuplicates(!library.hideDuplicatesEnabled) },
                            onSelectLetter = { viewModel.filterByLetter(it) },
                            onSelectArtist = { viewModel.filterByArtist(it) },
                            onOpenKeypad = { showKeypadDialog = true },
                            onPlaySong = { song ->
                                viewModel.playSong(song)
                                screen = AppScreen.PLAYING
                            },
                            onQueueSong = { song -> viewModel.addToQueue(song) },
                            onFavoriteSong = { song -> viewModel.setFavorite(song) },
                            onAddSongToPlaylistModal = { song -> songForPlaylistDialog = song },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onSelectPlaylist = { id -> viewModel.selectPlaylist(id) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                            onQueuePlaylist = { id ->
                                viewModel.queueEntirePlaylist(id)
                                screen = AppScreen.QUEUE
                            },
                            onRemoveSongFromPlaylist = { pid, sid -> viewModel.removeSongFromPlaylist(pid, sid) },
                            onOpenSetup = { screen = AppScreen.SETTINGS }
                        )
                        AppScreen.FAVORITES -> SongsLibraryScreen(
                            library = library,
                            activeTab = if (libraryTab == LibraryFilterTab.ALL) LibraryFilterTab.FAVORITES else libraryTab,
                            onSelectTab = { libraryTab = it },
                            onSearch = { viewModel.search(it) },
                            onToggleTypoTolerance = { viewModel.setTypoTolerance(!library.typoToleranceEnabled) },
                            onToggleHideDuplicates = { viewModel.setHideDuplicates(!library.hideDuplicatesEnabled) },
                            onSelectLetter = { viewModel.filterByLetter(it) },
                            onSelectArtist = { viewModel.filterByArtist(it) },
                            onOpenKeypad = { showKeypadDialog = true },
                            onPlaySong = { song ->
                                viewModel.playSong(song)
                                screen = AppScreen.PLAYING
                            },
                            onQueueSong = { song -> viewModel.addToQueue(song) },
                            onFavoriteSong = { song -> viewModel.setFavorite(song) },
                            onAddSongToPlaylistModal = { song -> songForPlaylistDialog = song },
                            onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                            onSelectPlaylist = { id -> viewModel.selectPlaylist(id) },
                            onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                            onQueuePlaylist = { id ->
                                viewModel.queueEntirePlaylist(id)
                                screen = AppScreen.QUEUE
                            },
                            onRemoveSongFromPlaylist = { pid, sid -> viewModel.removeSongFromPlaylist(pid, sid) },
                            onOpenSetup = { screen = AppScreen.SETTINGS }
                        )
                        AppScreen.QUEUE -> QueueAndSingersScreen(
                            library = library,
                            onPlayNext = {
                                viewModel.playNextFromQueue()
                                screen = AppScreen.PLAYING
                            },
                            onPlayQueueRowNow = { row ->
                                viewModel.playQueueRowImmediately(row)
                                screen = AppScreen.PLAYING
                            },
                            onMoveUp = { id -> viewModel.moveQueueItem(id, -1) },
                            onMoveDown = { id -> viewModel.moveQueueItem(id, 1) },
                            onMoveToTop = { id -> viewModel.moveQueueItemToTop(id) },
                            onRemove = { id -> viewModel.removeFromQueue(id) },
                            onClear = { viewModel.clearQueue() },
                            onSurpriseMe = { viewModel.surpriseMeQueue(5) },
                            onAddSinger = { name -> viewModel.addSingerToRotation(name) },
                            onSelectActiveSinger = { name -> viewModel.setActiveSingerName(name) },
                            onSetDuetPartner = { name -> viewModel.setActiveDuetPartner(name) },
                            onToggleSingerActive = { singer -> viewModel.toggleSingerActive(singer) },
                            onRemoveSinger = { id -> viewModel.removeSinger(id) },
                            onSetMode = { mode -> viewModel.setKaraokeMode(mode) },
                            onOpenStage = { screen = AppScreen.PLAYING }
                        )
                        AppScreen.PLAYING -> NowPlayingStageScreen(
                            library = library,
                            playback = playback,
                            onTogglePlay = { viewModel.togglePlayback() },
                            onRestart = { viewModel.restartCurrentSong() },
                            onNextSong = { viewModel.playNextFromQueue() },
                            onSeek = { viewModel.seekPlayback(it) },
                            onSkipInterlude = { viewModel.skipInterlude() },
                            onChangeKey = { viewModel.setKeyTranspose(it) },
                            onChangeTempo = { viewModel.setTempoMultiplier(it) },
                            onToggleMelody = { viewModel.setMelodyEnabled(!library.melodyEnabled) },
                            onOpenMixer = { showAudioMixerDialog = true },
                            onOpenSyncEditor = { showLyricsSyncEditor = true },
                            onOpenLatencyCalibration = { showLatencyCalibrationDialog = true },
                            onNudgePerSongLatency = { deltaMs ->
                                viewModel.updatePerSongLatencyOffset(library.latencyCalibration.perSongOffsetMs + deltaMs)
                            },
                            onToggleTwoLineLayout = {
                                viewModel.setTwoLineKaraokeLayout(!library.twoLineKaraokeLayout)
                            },
                            onToggleMicScoring = {
                                val next = !library.liveScoringEnabled
                                viewModel.setLiveScoringEnabled(next)
                                if (next) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onToggleFullScreen = { viewModel.setFullScreenStage(!library.fullScreenStage) },
                            onBrowseSongs = { screen = AppScreen.SONGS }
                        )
                        AppScreen.SETTINGS -> SetupAndSettingsScreen(
                            library = library,
                            onCheckNewLibrary = { viewModel.checkForNewLibrary() },
                            onDownloadLibraryUpdates = { viewModel.downloadLibraryUpdates() },
                            onStartSetup = { viewModel.setup() },
                            onPauseSetup = { viewModel.pauseSetup() },
                            onSelectSoundFont = { viewModel.selectSoundFont(it) },
                            onImportSoundFont = { soundFontPicker.launch(arrayOf("*/*")) },
                            onImportCustomSong = { customSongPicker.launch(arrayOf("*/*")) },
                            onSelectBackground = { viewModel.selectDownloadedBackground(it) },
                            onChooseDeviceVideo = { videoPicker.launch(arrayOf("video/*", "image/*")) },
                            onClearBackground = { viewModel.clearBackgroundVideo() },
                            onChangeLyricFontScale = { viewModel.setLyricFontSizeScale(it) },
                            onChangeLyricPalette = { viewModel.setLyricPaletteName(it) },
                            onChangeVisualizerStyle = { viewModel.setVisualizerStyle(it) },
                            onToggleAutoPlayNext = { viewModel.setAutoPlayNextEnabled(it) },
                            onToggleVoiceAnnouncer = { viewModel.setVoiceAnnouncerEnabled(it) },
                            onToggleMicScoring = { enabled ->
                                viewModel.setLiveScoringEnabled(enabled)
                                if (enabled) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenRemoteQr = { showQrRemoteDialog = true },
                            onOpenMixer = { showAudioMixerDialog = true },
                            onCheckUpdates = { viewModel.checkForUpdates() },
                            onPlaySampleSoundFont = { path, inst -> viewModel.playSampleSoundFont(path, inst) },
                            onStopSampleSoundFont = { viewModel.stopSampleSoundFont() },
                            onChangeFontFamily = { viewModel.setLyricFontFamily(it) },
                            onChangeFontWeight = { viewModel.setLyricFontWeight(it) },
                            onChangeLetterSpacing = { viewModel.setLyricLetterSpacing(it) },
                            onChangeLineSpacing = { viewModel.setLyricLineSpacing(it) },
                            onChangeTextAlignment = { viewModel.setLyricTextAlignment(it) },
                            onChangeStagePosition = { viewModel.setLyricStagePosition(it) },
                            onChangeOutlineThickness = { viewModel.setLyricOutlineThickness(it) },
                            onChangeShadowIntensity = { viewModel.setLyricShadowIntensity(it) },
                            onChangeDisplayMode = { viewModel.setLyricDisplayMode(it) },
                            onChangeStageElement = { name, enabled -> viewModel.setStageElement(name, enabled) },
                            onChangePhraseGrouping = { viewModel.setLyricPhraseGrouping(it) },
                            onChangeBackgroundBrightness = { viewModel.setBackgroundBrightness(it) },
                            onChangeLyricsScrimAlpha = { viewModel.setLyricsScrimAlpha(it) },
                            onChangeVisualizerSensitivity = { viewModel.setVisualizerSensitivity(it) },
                            onChangeVisualizerSpeed = { viewModel.setVisualizerSpeed(it) },
                            onChangeVisualizerBrightness = { viewModel.setVisualizerBrightness(it) },
                    onChangeVisualizerIntensityPreset = { viewModel.setVisualizerIntensityPreset(it) },
                    onChangeVisualizerSafeZone = { viewModel.setVisualizerLyricsSafeZone(it) },
                    onChangeVisualizerGlowStrength = { viewModel.setVisualizerGlowStrength(it) },
                    onChangeVisualizerPerformanceMode = { viewModel.setVisualizerPerformanceMode(it) },
                            onChangeCustomPaletteColors = { a, g, u, n -> viewModel.setCustomPaletteColors(a, g, u, n) },
                            onResetLyricsTypography = { viewModel.resetLyricsTypographyDefaults() },
                            onResetWipePalettes = { viewModel.resetWipePalettesDefaults() },
                            onResetVisualizer = { viewModel.resetVisualizerDefaults() },
                            onResetBackgroundVideo = { viewModel.resetBackgroundVideoDefaults() },
                            onOpenSyncEditor = { showLyricsSyncEditor = true },
                            onSelectDesignStyle = { viewModel.setDesignStyle(it) },
                            onSelectColorStyle = { viewModel.setColorStyle(it) },
                            onSetCustomThemeColors = { p, s -> viewModel.setCustomThemeColors(p, s) },
                            onResetThemeDefaults = { viewModel.resetThemeDefaults() }
                        )
                    }
                }
            }
        }

        // Modals & Overlays
        if (showKeypadDialog) {
            SongNumberKeypadDialog(
                songs = library.songs,
                onLookupMatches = { digits -> viewModel.search(digits) },
                onConfirmCode = { code, playNow ->
                    viewModel.playOrQueueByNumberCode(code, playNow)
                    if (playNow) screen = AppScreen.PLAYING
                },
                onDismiss = { showKeypadDialog = false }
            )
        }

        if (showQrRemoteDialog) {
            WifiRemoteQrDialog(
                remoteState = library.remoteServerState,
                onToggleServer = { viewModel.toggleWifiRemoteServer(it) },
                onDismiss = { showQrRemoteDialog = false }
            )
        }

        if (showAudioMixerDialog) {
            AudioMixerDialog(
                config = library.mixerConfig,
                melodyEnabled = library.melodyEnabled,
                onMelodyToggle = { viewModel.setMelodyEnabled(it) },
                onUpdateConfig = { viewModel.updateMixerConfig(it) },
                onReset = { viewModel.updateMixerConfig(AudioMixerConfig()) },
                onDismiss = { showAudioMixerDialog = false }
            )
        }

        if (showLyricsSyncEditor) {
            LyricsSyncEditorDialog(
                songTitle = library.currentSong?.title ?: "JKTV Karaoke Stage",
                playback = library.playback,
                palette = resolveLyricPalette(library.lyricPaletteName),
                onTogglePlay = { viewModel.togglePlayback() },
                onSeek = { viewModel.seekPlayback(it) },
                onPreviewLinesChanged = { viewModel.previewEditedLyrics(it) },
                onSaveSync = { viewModel.saveCustomSyncedLyrics(it) },
                onRestoreOriginal = { viewModel.restoreOriginalSongLyrics() },
                onImportTimingFile = { lrcImportPicker.launch(arrayOf("*/*")) },
                onExportLrcFile = { lines ->
                    pendingLrcExportLines = lines
                    val defaultName = (library.currentSong?.title ?: "jktv-lyrics")
                        .replace(Regex("[^A-Za-z0-9._ -]"), "_") + ".lrc"
                    lrcExportLauncher.launch(defaultName)
                },
                onDismiss = { showLyricsSyncEditor = false }
            )
        }

        if (showLatencyCalibrationDialog) {
            LatencyCalibrationDialog(
                calibration = library.latencyCalibration,
                songTitle = library.currentSong?.title,
                onUpdateGlobalOffset = { viewModel.updateGlobalLatencyOffset(it) },
                onUpdatePerSongOffset = { viewModel.updatePerSongLatencyOffset(it) },
                onUpdateBluetoothOffset = { viewModel.updateBluetoothLatencyOffset(it) },
                onToggleBluetoothAuto = { viewModel.setBluetoothAutoCompensation(it) },
                onResetAll = { viewModel.resetLatencyCalibration() },
                onDismiss = { showLatencyCalibrationDialog = false }
            )
        }

        val scoreCard = library.finalScoreCard
        if (scoreCard != null) {
            ScoreCardDialog(
                card = scoreCard,
                onDismiss = { viewModel.dismissScoreCard() }
            )
        }

        val playlistTargetSong = songForPlaylistDialog
        if (playlistTargetSong != null) {
            AddToPlaylistDialog(
                song = playlistTargetSong,
                playlists = library.playlists,
                onCreateAndAdd = { name ->
                    viewModel.createPlaylist(name)
                },
                onSelectPlaylist = { pid ->
                    viewModel.addSongToPlaylist(pid, playlistTargetSong.id)
                    songForPlaylistDialog = null
                },
                onDismiss = { songForPlaylistDialog = null }
            )
        }

        if (library.updateMessage != null || library.updateInfo != null || library.libraryUpdateManifest != null) {
            UpdateStatusDialog(
                library = library,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onDownloadApk = { viewModel.downloadAndInstallUpdate() },
                onInstallApk = { viewModel.installDownloadedUpdate() },
                onUpdateLibrary = {
                    viewModel.dismissUpdateDialog()
                    screen = AppScreen.SETTINGS
                    viewModel.checkForNewLibrary()
                }
            )
        }
        }
    }
}

@Composable
private fun FuturisticTopBar(
    library: LibraryUiState,
    castController: com.jktv.offline.cast.CastController,
    onOpenKeypad: () -> Unit,
    onOpenRemoteQr: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val headerConfiguration = LocalConfiguration.current
    val compactActions = headerConfiguration.screenWidthDp / headerConfiguration.fontScale < 480
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Compact JKTV Logo Badge with Subtle Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_jktv_logo),
                    contentDescription = "JKTV Official Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (library.installed) Color(0xFF00FF9D) else Color(0xFFFFB300))
                )
            }

            // Right: Compact Keypad Pill, Wi-Fi QR Remote Pill, and Minimalist Updates Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Keypad Button (Compact, accessible)
                com.jktv.offline.cast.CastControls(castController)
                Surface(
                    onClick = onOpenKeypad,
                    shape = RoundedCornerShape(8.dp),
                    color = primaryColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier.height(48.dp).widthIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.Dialpad,
                            contentDescription = "Song Number Keypad",
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        if (!compactActions) Text(
                            text = "Keypad",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }

                // Wi-Fi QR Remote Button (Compact, accessible)
                val isRemoteRunning = library.remoteServerState.running
                val qrTint = if (isRemoteRunning) Color(0xFF00FF9D) else Color(0xFFFFD600)
                Surface(
                    onClick = onOpenRemoteQr,
                    shape = RoundedCornerShape(8.dp),
                    color = qrTint.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, qrTint.copy(alpha = 0.35f)),
                    modifier = Modifier.height(48.dp).widthIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            contentDescription = "Wi-Fi QR Remote",
                            tint = qrTint,
                            modifier = Modifier.size(16.dp)
                        )
                        if (!compactActions) Text(
                            text = if (isRemoteRunning) "QR Remote" else "QR Pair",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = qrTint
                        )
                    }
                }

                // Minimalist Check Updates Icon Button (Doesn't clutter header)
                IconButton(
                    onClick = onCheckUpdates,
                    enabled = !library.updateCheckBusy && !library.updateDownloadBusy,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (library.updateCheckBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = primaryColor
                        )
                    } else {
                        Icon(
                            Icons.Default.SystemUpdateAlt,
                            contentDescription = "Check for Updates",
                            tint = Color(0xFF9FB2D9),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStageNowPlayingBar(
    currentSong: SongEntity?,
    isPlaying: Boolean,
    hasQueue: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenStage: () -> Unit
) {
    val song = currentSong ?: return
    Surface(
        color = Color(0xFF101935),
        border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenStage)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NOW SINGING • #${song.displayNumber}",
                    color = Color(0xFF00F0FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${song.title} — ${song.artist.ifBlank { "Karaoke" }}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color(0xFF00F0FF)
                    )
                }
                if (hasQueue) {
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Song", tint = Color(0xFFFF2A85))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    library: LibraryUiState,
    onSelectScreen: (AppScreen) -> Unit,
    onOpenKeypad: () -> Unit,
    onOpenRemoteQr: () -> Unit,
    onSurpriseMe: () -> Unit,
    onPlaySong: (SongEntity) -> Unit,
    onQueueSong: (SongEntity) -> Unit,
    onFavoriteSong: (SongEntity) -> Unit,
    onSetMode: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Prominent Quick Search Card (Instant tap-to-search 50,000+ songs)
        item {
            Surface(
                onClick = { onSelectScreen(AppScreen.SONGS) },
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0E162D),
                border = BorderStroke(1.dp, Color(0xFF1F2F56)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(22.dp))
                    Text(
                        text = "Search 50,000+ songs, artists, or #number…",
                        color = Color(0xFF8EA3CA),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF162447)
                    ) {
                        Text(
                            text = "50K+ SONGS",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Quick Category Discovery Pills (Instant 1-tap browsing)
        item {
            val quickCategories = listOf(
                "Top Hits" to "POPULAR",
                "OPM Classics" to "OPM",
                "Duets" to "DUET",
                "90s Rock" to "ROCK",
                "Party Favorites" to "FAV",
                "Recently Sung" to "RECENT"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickCategories.forEach { (label, categoryKey) ->
                    Surface(
                        onClick = {
                            when (categoryKey) {
                                "FAV" -> onSelectScreen(AppScreen.FAVORITES)
                                else -> onSelectScreen(AppScreen.SONGS)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Futuristic Stage Hero Banner
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.45f)),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF131E42),
                                Color(0xFF21113E),
                                Color(0xFF0B132B)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF00F0FF).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF))
                        ) {
                            Text(
                                text = if (library.installed) "100% OFFLINE STUDIO ENGINE" else "SETUP REQUIRED",
                                color = Color(0xFF00F0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        if (library.remoteServerState.running) {
                            Text(
                                text = "📡 Remote Active (PIN ${library.remoteServerState.pin})",
                                color = Color(0xFF00FF9D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = library.currentSong?.let { "Now On Stage: ${it.title}" }
                            ?: "Light Up Your Karaoke Night",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = library.currentSong?.let {
                            "Artist: ${it.artist.ifBlank { "Karaoke Arrangement" }} • Key ${formatSigned(library.keyTranspose)} • Tempo ${(library.tempoMultiplier * 100).roundToInt()}%"
                        } ?: "High-precision FluidSynth SF2 audio, syllable-by-syllable neon lyrics, live pitch scoring, and instant 50,000+ song search.",
                        color = Color(0xFFC5D5F5),
                        fontSize = 13.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (library.installed) onSelectScreen(AppScreen.SONGS)
                                else onSelectScreen(AppScreen.SETTINGS)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00F0FF),
                                contentColor = Color(0xFF040914)
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (library.installed) "Search Songs" else "Install Song Library", fontWeight = FontWeight.Black)
                        }
                        FilledTonalButton(
                            onClick = onOpenKeypad,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Song # Keypad", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { onSelectScreen(AppScreen.PLAYING) },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Stage", fontWeight = FontWeight.Bold)
                        }
                        if (library.installed) {
                            OutlinedButton(
                                onClick = onSurpriseMe,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Party Shuffle +5")
                            }
                        }
                    }
                }
            }
        }

        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatNeonCard(
                    title = "SONGS",
                    value = "${library.songCount}",
                    subtitle = if (library.installed) "Indexed & Ready" else "Tap Setup",
                    accent = Color(0xFF00F0FF),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.SONGS) }
                )
                StatNeonCard(
                    title = "QUEUE",
                    value = "${library.queue.size}",
                    subtitle = library.queue.firstOrNull()?.singer?.let { "Next: $it" } ?: "No songs queued",
                    accent = Color(0xFFFF2A85),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.QUEUE) }
                )
                StatNeonCard(
                    title = "SOUNDFONT",
                    value = "${library.soundFonts.size} SF2",
                    subtitle = library.soundFonts.firstOrNull { it.path == library.selectedSoundFontPath }?.name ?: "Default GM",
                    accent = Color(0xFFFFD600),
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectScreen(AppScreen.SETTINGS) }
                )
            }
        }

        // Karaoke Performance Mode Selector (Solo / Duet / Party / Practice)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0E152B),
                border = BorderStroke(1.dp, Color(0xFF223159)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KARAOKE STAGE MODE",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Active Singer: 🎤 ${library.activeSingerName}",
                            color = Color(0xFFFFD600),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onSelectScreen(AppScreen.QUEUE) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            "SOLO" to "🎤 Solo Stage",
                            "DUET" to "👥 Duet Mode",
                            "PARTY" to "🎉 Party Rotation",
                            "PRACTICE" to "🎯 Practice & Pitch Guide"
                        )
                        for ((modeId, label) in modes) {
                            val selected = library.karaokeMode == modeId
                            FilterChip(
                                selected = selected,
                                onClick = { onSetMode(modeId) },
                                label = { Text(label, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00F0FF),
                                    selectedLabelColor = Color(0xFF040914)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Up Next Queue Preview
        if (library.queue.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "UP NEXT ON STAGE (${library.queue.size})",
                    actionLabel = "Manage Queue",
                    onAction = { onSelectScreen(AppScreen.QUEUE) }
                )
            }
            items(library.queue.take(3), key = { "home_q_${it.id}" }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF101935),
                    border = BorderStroke(1.dp, Color(0xFFFF2A85).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${item.displayNumber} • ${item.title}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "🎤 ${item.singer}${if (item.secondSinger.isNotBlank()) " & ${item.secondSinger}" else ""} [${item.mode}]",
                                color = Color(0xFFFFD600),
                                fontSize = 12.sp
                            )
                        }
                        FilledTonalButton(onClick = { onSelectScreen(AppScreen.QUEUE) }) {
                            Text("Queue")
                        }
                    }
                }
            }
        }

        // Recently Played Songs
        if (library.recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "RECENTLY SUNG",
                    actionLabel = "See All",
                    onAction = { onSelectScreen(AppScreen.SONGS) }
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(library.recentlyPlayed.take(12), key = { "rec_${it.id}" }) { song ->
                        CompactSongTile(
                            song = song,
                            accent = Color(0xFF00F0FF),
                            onPlay = { onPlaySong(song) },
                            onQueue = { onQueueSong(song) }
                        )
                    }
                }
            }
        }

        // Favorites Preview
        if (library.favorites.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "FAVORITE HITS (${library.favorites.size})",
                    actionLabel = "All Favorites",
                    onAction = { onSelectScreen(AppScreen.FAVORITES) }
                )
            }
            items(library.favorites.take(5), key = { "fav_home_${it.id}" }) { song ->
                SongItemCard(
                    song = song,
                    onPlay = { onPlaySong(song) },
                    onQueue = { onQueueSong(song) },
                    onFavorite = { onFavoriteSong(song) },
                    onAddToPlaylist = null
                )
            }
        }
    }
}

@Composable
private fun StatNeonCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0E152B),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                subtitle,
                color = Color(0xFF9FB2D9),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactSongTile(
    song: SongEntity,
    accent: Color,
    onPlay: () -> Unit,
    onQueue: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101830),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.width(210.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "#${song.displayNumber}",
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(
                text = song.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist.ifBlank { "Karaoke Arrangement" },
                color = Color(0xFF9FB2D9),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onQueue,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+Queue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Sing", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF00F0FF),
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
        TextButton(onClick = onAction) {
            Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongsLibraryScreen(
    library: LibraryUiState,
    activeTab: LibraryFilterTab,
    onSelectTab: (LibraryFilterTab) -> Unit,
    onSearch: (String) -> Unit,
    onToggleTypoTolerance: () -> Unit,
    onToggleHideDuplicates: () -> Unit,
    onSelectLetter: (String?) -> Unit,
    onSelectArtist: (String?) -> Unit,
    onOpenKeypad: () -> Unit,
    onPlaySong: (SongEntity) -> Unit,
    onQueueSong: (SongEntity) -> Unit,
    onFavoriteSong: (SongEntity) -> Unit,
    onAddSongToPlaylistModal: (SongEntity) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onQueuePlaylist: (Long) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit,
    onOpenSetup: () -> Unit
) {
    var newPlaylistName by rememberSaveable { mutableStateOf("") }
    var localSearchQuery by rememberSaveable(library.searchQuery) { mutableStateOf(library.searchQuery) }

    LaunchedEffect(localSearchQuery) {
        if (localSearchQuery != library.searchQuery) {
            delay(180L)
            onSearch(localSearchQuery)
        }
    }

    val displayedSongs: List<SongEntity> = remember(activeTab, library.songs, library.favorites, library.recentlyPlayed, library.mostPlayed, library.selectedPlaylistSongs) {
        when (activeTab) {
            LibraryFilterTab.ALL -> library.songs
            LibraryFilterTab.FAVORITES -> if (library.searchQuery.isNotBlank()) {
                library.songs.filter { it.favorite }
            } else library.favorites
            LibraryFilterTab.RECENT -> library.recentlyPlayed
            LibraryFilterTab.POPULAR -> library.mostPlayed
            LibraryFilterTab.PLAYLISTS -> library.selectedPlaylistSongs
            LibraryFilterTab.ARTISTS -> library.songs
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Super-Fast Search Header
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0E162D),
                border = BorderStroke(1.dp, Color(0xFF243563)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = {
                                localSearchQuery = it
                                if (activeTab != LibraryFilterTab.ALL && activeTab != LibraryFilterTab.FAVORITES) {
                                    onSelectTab(LibraryFilterTab.ALL)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00F0FF)) },
                            trailingIcon = {
                                if (localSearchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        localSearchQuery = ""
                                        onSearch("")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            label = { Text("Search 50,000+ songs by title, artist, or #number") }
                        )
                        FilledTonalIconButton(
                            onClick = onOpenKeypad,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(Icons.Default.Dialpad, contentDescription = "Keypad", tint = Color(0xFF00F0FF))
                        }
                    }

                    // Filter Tabs Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LibraryFilterTab.entries.forEach { tab ->
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { onSelectTab(tab) },
                                label = { Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00F0FF),
                                    selectedLabelColor = Color(0xFF040914)
                                )
                            )
                        }
                    }

                    // Search Intelligence Toggles (Typo Tolerance & Duplicate Detection)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = library.typoToleranceEnabled,
                            onClick = onToggleTypoTolerance,
                            label = { Text("✨ Typo-Tolerant Fuzzy Search", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = library.hideDuplicatesEnabled,
                            onClick = onToggleHideDuplicates,
                            label = {
                                Text(
                                    if (library.duplicateCountRemoved > 0) {
                                        "Hide Duplicates (${library.duplicateCountRemoved} hidden)"
                                    } else {
                                        "Hide Duplicates"
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    // Instant Autocomplete Suggestion Chips
                    if (library.searchSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            library.searchSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        localSearchQuery = suggestion
                                        onSearch(suggestion)
                                    },
                                    label = { Text(suggestion, fontSize = 11.sp, maxLines = 1) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF162244),
                                        labelColor = Color(0xFF00F0FF)
                                    )
                                )
                            }
                        }
                    }

                    // A–Z Fast Jump Bar
                    if (activeTab == LibraryFilterTab.ALL) {
                        val letters = remember { listOf("ALL") + ('A'..'Z').map { it.toString() } }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(letters, key = { "az_$it" }) { letter ->
                                val isSel = (letter == "ALL" && library.selectedLetterFilter == null) ||
                                    library.selectedLetterFilter == letter
                                Surface(
                                    onClick = { onSelectLetter(if (letter == "ALL") null else letter) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) Color(0xFF00F0FF) else Color(0xFF152042)
                                ) {
                                    Text(
                                        text = letter,
                                        color = if (isSel) Color(0xFF040914) else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playlists Tab Header & Selector
        if (activeTab == LibraryFilterTab.PLAYLISTS) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF101935),
                    border = BorderStroke(1.dp, Color(0xFFFF2A85).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("CUSTOM KARAOKE PLAYLISTS", color = Color(0xFFFF2A85), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("New Playlist Name (e.g. Friday Party Hits)") }
                            )
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        onCreatePlaylist(newPlaylistName, "Custom Karaoke Setlist")
                                        newPlaylistName = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Create")
                            }
                        }

                        if (library.playlists.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(library.playlists, key = { "pl_${it.id}" }) { pl ->
                                    val selected = pl.id == library.selectedPlaylistId
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectPlaylist(pl.id) },
                                        label = { Text("${pl.name} (${pl.songCount})", fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                            val currentPl = library.playlists.firstOrNull { it.id == library.selectedPlaylistId }
                            if (currentPl != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onQueuePlaylist(currentPl.id) },
                                        enabled = currentPl.songCount > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Queue All (${currentPl.songCount})")
                                    }
                                    OutlinedButton(onClick = { onDeletePlaylist(currentPl.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete Playlist")
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Create a playlist above, then tap the +Playlist icon on any song card to build your setlist.",
                                fontSize = 12.sp,
                                color = Color(0xFF9FB2D9)
                            )
                        }
                    }
                }
            }
        }

        // Artists Tab Chips
        if (activeTab == LibraryFilterTab.ARTISTS && library.topArtists.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF101935),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("BROWSE TOP ARTISTS", color = Color(0xFF00F0FF), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(library.topArtists, key = { "art_${it.artist}" }) { row ->
                                FilterChip(
                                    selected = library.selectedArtistFilter == row.artist,
                                    onClick = { onSelectArtist(row.artist) },
                                    label = { Text("${row.artist} (${row.songCount})", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!library.installed) {
            item {
                EmptyLibraryBanner(onOpenSetup = onOpenSetup)
            }
        } else if (displayedSongs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0E152A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No songs match this view", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Try another keyword, toggle Typo-Tolerant search, or clear the current filter.",
                            fontSize = 12.sp,
                            color = Color(0xFF9FB2D9)
                        )
                    }
                }
            }
        } else {
            // True LazyColumn items() with contentType recycling for 60/120 FPS scrolling on 50,000+ songs!
            items(
                items = displayedSongs,
                key = { song -> song.id },
                contentType = { "song_item" }
            ) { song ->
                SongItemCard(
                    song = song,
                    onPlay = { onPlaySong(song) },
                    onQueue = { onQueueSong(song) },
                    onFavorite = { onFavoriteSong(song) },
                    onAddToPlaylist = { onAddSongToPlaylistModal(song) },
                    onRemoveFromPlaylist = if (activeTab == LibraryFilterTab.PLAYLISTS && library.selectedPlaylistId != null) {
                        { onRemoveSongFromPlaylist(library.selectedPlaylistId, song.id) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun rememberClickGuard(debounceMs: Long = 300L): () -> Boolean {
    var lastClickTime by remember { mutableStateOf(0L) }
    return {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickTime >= debounceMs) {
            lastClickTime = now
            true
        } else {
            false
        }
    }
}

@Composable
private fun SongItemCard(
    song: SongEntity,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    val clickGuard = rememberClickGuard()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "#${song.displayNumber}",
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(song.artist.ifBlank { "Karaoke Arrangement" })
                            if (song.playCount > 0) append(" • Sung ${song.playCount}x")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (onAddToPlaylist != null) {
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (onRemoveFromPlaylist != null) {
                    IconButton(
                        onClick = onRemoveFromPlaylist,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove from Playlist",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Ergonomic 44dp-48dp action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { if (clickGuard()) onQueue() },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("+ Queue", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (clickGuard()) onPlay() },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sing Now", fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun QueueAndSingersScreen(
    library: LibraryUiState,
    onPlayNext: () -> Unit,
    onPlayQueueRowNow: (QueueRow) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onMoveToTop: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onSurpriseMe: () -> Unit,
    onAddSinger: (String) -> Unit,
    onSelectActiveSinger: (String) -> Unit,
    onSetDuetPartner: (String) -> Unit,
    onToggleSingerActive: (com.jktv.offline.data.SingerEntity) -> Unit,
    onRemoveSinger: (Long) -> Unit,
    onSetMode: (String) -> Unit,
    onOpenStage: () -> Unit
) {
    var singerInput by rememberSaveable { mutableStateOf("") }
    var duetInput by rememberSaveable { mutableStateOf(library.activeDuetPartner) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Stage Now Singing & Up Next Card (fixes hardcoded "No song playing" bug!)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF121C3A),
                border = BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("NOW SINGING ON STAGE", color = Color(0xFF00F0FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(
                                text = library.currentSong?.let { "#${it.displayNumber} ${it.title}" } ?: "Stage Idle — Ready for Next Singer",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = library.currentSong?.let {
                                    "🎤 ${library.currentQueueRow?.singer ?: library.activeSingerName} • ${it.artist.ifBlank { "Karaoke" }}"
                                } ?: "Tap 'Start Next Song' below or pick any song from the library.",
                                color = Color(0xFFB5C7E8),
                                fontSize = 12.sp
                            )
                        }
                        if (library.currentSong != null) {
                            Button(onClick = onOpenStage) {
                                Text("Stage View", fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val upNext = library.queue.firstOrNull()
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UP NEXT IN QUEUE (${library.queue.size} WAITING)", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(
                                text = upNext?.let { "#${it.displayNumber} ${it.title} — 🎤 ${it.singer}" } ?: "Queue is empty",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (library.queue.isNotEmpty()) {
                                Button(
                                    onClick = onPlayNext,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF2A85),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start Next")
                                }
                            } else {
                                FilledTonalButton(onClick = onSurpriseMe) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("+5 Random")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Singer Rotation & Duet Manager Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0E152B),
                border = BorderStroke(1.dp, Color(0xFF233460)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("SINGER ROTATION & DUET MANAGER", color = Color(0xFF00F0FF), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = singerInput,
                            onValueChange = { singerInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Add Singer to Party Rotation") }
                        )
                        Button(
                            onClick = {
                                if (singerInput.isNotBlank()) {
                                    onAddSinger(singerInput)
                                    singerInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (library.karaokeMode == "DUET") {
                        OutlinedTextField(
                            value = duetInput,
                            onValueChange = {
                                duetInput = it
                                onSetDuetPartner(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Duet Partner Name (for Duet Mode reservations)") }
                        )
                    }

                    if (library.singers.isNotEmpty()) {
                        Text(
                            "Tap a singer chip to set as current reserving singer (auto-rotates when active):",
                            fontSize = 11.sp,
                            color = Color(0xFF9FB2D9)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(library.singers, key = { "singer_${it.id}" }) { singer ->
                                val isCurrent = singer.name.equals(library.activeSingerName, ignoreCase = true)
                                Surface(
                                    onClick = { onSelectActiveSinger(singer.name) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrent) Color(0xFF00F0FF).copy(alpha = 0.22f) else Color(0xFF152042),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) Color(0xFF00F0FF) else Color.White.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = buildString {
                                                append("🎤 ${singer.name}")
                                                if (singer.bestScore > 0) append(" (🏆 ${singer.bestScore})")
                                            },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        IconButton(
                                            onClick = { onRemoveSinger(singer.id) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Remove singer",
                                                tint = Color(0xFF9FB2D9),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Queue Action Bar
        if (library.queue.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "RESERVED SONG QUEUE (${library.queue.size})",
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSurpriseMe) {
                            Text("+5 Random", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onClear) {
                            Text("Clear All", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Queue Items with Top / Up / Down / Play Now / Remove controls
        itemsIndexed(library.queue, key = { _, item -> "q_row_${item.id}" }) { index, item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0E162C),
                border = BorderStroke(
                    1.dp,
                    if (index == 0) Color(0xFFFFD600).copy(alpha = 0.6f) else Color(0xFF1E2D56)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${index + 1}.",
                                color = Color(0xFFFFD600),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "#${item.displayNumber}",
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF1A2850)
                            ) {
                                Text(
                                    text = item.mode,
                                    color = Color(0xFF00F0FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("🎤 ${item.singer}")
                                if (item.secondSinger.isNotBlank()) append(" & ${item.secondSinger}")
                                if (item.artist.isNotBlank()) append(" • ${item.artist}")
                            },
                            color = Color(0xFF9FB2D9),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (index > 0) {
                            IconButton(
                                onClick = { onMoveToTop(item.id) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.VerticalAlignTop, contentDescription = "Move to Top", tint = Color(0xFFFFD600), modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { onMoveUp(item.id) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (index < library.queue.lastIndex) {
                            IconButton(
                                onClick = { onMoveDown(item.id) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { onPlayQueueRowNow(item) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Sing Now", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { onRemove(item.id) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingStageScreen(
    library: LibraryUiState,
    playback: PlaybackState,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onNextSong: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipInterlude: () -> Unit,
    onChangeKey: (Int) -> Unit,
    onChangeTempo: (Float) -> Unit,
    onToggleMelody: () -> Unit,
    onOpenMixer: () -> Unit,
    onOpenSyncEditor: () -> Unit,
    onOpenLatencyCalibration: () -> Unit,
    onNudgePerSongLatency: (Long) -> Unit,
    onToggleTwoLineLayout: () -> Unit,
    onToggleMicScoring: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onBrowseSongs: () -> Unit
) {
    val basePalette = resolveLyricPalette(library.lyricPaletteName)
    val palette = if (library.lyricPaletteName == "CUSTOM_PALETTE") {
        basePalette.copy(
            unsungFill = parseHexColor(library.customUnsungFillHex, basePalette.unsungFill),
            activeFill = parseHexColor(library.customActiveFillHex, basePalette.activeFill),
            activeGlow = parseHexColor(library.customActiveGlowHex, basePalette.activeGlow),
            nextLineFill = parseHexColor(library.customNextLineFillHex, basePalette.nextLineFill)
        )
    } else basePalette

    if (library.fullScreenStage) {
        // Immersive Full-Screen Karaoke Stage Mode
        var controlsVisible by remember { mutableStateOf(true) }

        // Auto-hide controls after 4 seconds of playback
        LaunchedEffect(controlsVisible, playback.playing) {
            if (controlsVisible && playback.playing) {
                delay(4000L)
                controlsVisible = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040711))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    controlsVisible = !controlsVisible
                }
        ) {
            StageBackdropLayer(
                backgroundUri = library.backgroundVideoUri,
                positionMs = playback.positionMs,
                backgroundIsImage = library.backgroundIsImage,
                visualizerStyle = library.visualizerStyle,
                channelLevels = playback.channelLevels,
                playing = playback.playing,
                palette = palette,
                brightness = library.backgroundBrightness,
                scrimAlpha = library.lyricsScrimAlpha,
                visualizerSensitivity = library.visualizerSensitivity,
                visualizerSpeed = library.visualizerSpeed,
                visualizerBrightness = library.visualizerBrightness,
                lyricsSafeZone = library.visualizerLyricsSafeZone,
                glowStrength = library.visualizerGlowStrength,
                performanceMode = library.visualizerPerformanceMode
            )

            // Stage Lyrics Area (Centered, fills screen)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = when (library.lyricStagePosition) { "TOP" -> Alignment.TopCenter; "BOTTOM" -> Alignment.BottomCenter; else -> Alignment.Center }
            ) {
                SyllableKaraokeStage(
                    lyricsEnabled = library.lyricsEnabled, countdownEnabled = library.countdownEnabled,
                    wipeEnabled = library.lyricWipeEnabled, singerInfoEnabled = library.singerInfoEnabled,
                    playback = playback,
                    palette = palette,
                    fontScale = (library.lyricFontSizeScale * 1.15f).coerceAtMost(2.0f),
                    karaokeMode = library.karaokeMode,
                    activeSinger = library.currentQueueRow?.singer ?: library.activeSingerName,
                    duetPartner = library.currentQueueRow?.secondSinger ?: library.activeDuetPartner,
                    onSkipInterlude = onSkipInterlude,
                    twoLineMode = library.twoLineKaraokeLayout,
                    displayMode = library.lyricDisplayMode,
                    fontFamilyChoice = library.lyricFontFamily,
                    fontWeightChoice = library.lyricFontWeight,
                    letterSpacingSp = library.lyricLetterSpacing,
                    lineSpacingDp = library.lyricLineSpacing,
                    textAlignment = library.lyricTextAlignment,
                    stagePosition = library.lyricStagePosition,
                    outlineThicknessDp = library.lyricOutlineThickness,
                    shadowIntensity = library.lyricShadowIntensity,
                    onOpenSyncEditor = onOpenSyncEditor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Top Menu Overlay (auto-hides after 4s, click screen to restore)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xE6040711),
                                    Color(0xB3040711),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (library.songInfoEnabled) (library.currentSong?.let { "#${it.displayNumber} • ${it.title}" } ?: "JKTV Stage") else "",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (library.songInfoEnabled) library.currentSong?.artist.orEmpty().ifBlank { "Offline MIDI/KAR Stage" } else "",
                            color = palette.activeFill,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onToggleTwoLineLayout) {
                            Text(if (library.twoLineKaraokeLayout) "2-Line" else "4-Line", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalIconButton(onClick = onOpenSyncEditor) {
                            Icon(Icons.Default.EditNote, contentDescription = "Lyrics Sync Studio", tint = Color(0xFFFFD600))
                        }
                        FilledTonalIconButton(onClick = onTogglePlay) {
                            Icon(
                                if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        if (library.queue.isNotEmpty()) {
                            FilledTonalIconButton(onClick = onNextSong) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Song")
                            }
                        }
                        FilledTonalIconButton(onClick = onToggleFullScreen) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Full Screen")
                        }
                    }
                }
            }

            // Bottom Pitch Scoring Bar (if enabled)
            if (library.liveScoringEnabled || library.karaokeMode == "PRACTICE") {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xCC040711)
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        LivePitchScoringBar(
                            scoreState = library.liveScoreState,
                            primaryColor = palette.activeFill,
                            accentColor = Color(0xFFFF2A85)
                        )
                    }
                }
            }
        }
        return
    }

    // Standard Stage View with Full Real-Time Control Deck
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main Karaoke Video + Visualizer + Syllable Stage Box
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, palette.activeFill.copy(alpha = 0.5f)),
                color = Color(0xFF050814),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 330.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 330.dp)) {
                    StageBackdropLayer(
                        backgroundUri = library.backgroundVideoUri,
                positionMs = playback.positionMs,
                backgroundIsImage = library.backgroundIsImage,
                        visualizerStyle = library.visualizerStyle,
                        channelLevels = playback.channelLevels,
                        playing = playback.playing,
                        palette = palette,
                        brightness = library.backgroundBrightness,
                        scrimAlpha = library.lyricsScrimAlpha,
                        visualizerSensitivity = library.visualizerSensitivity,
                        visualizerSpeed = library.visualizerSpeed,
                        visualizerBrightness = library.visualizerBrightness,
                        modifier = Modifier.matchParentSize()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = library.currentSong?.let { "#${it.displayNumber} • ${it.title}" }
                                        ?: "No Song Loaded",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = library.currentSong?.artist?.ifBlank { "Karaoke Arrangement" }
                                        ?: "Choose a song from Library or Queue",
                                    color = palette.activeFill,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = library.twoLineKaraokeLayout,
                                    onClick = onToggleTwoLineLayout,
                                    label = {
                                        Text(
                                            if (library.twoLineKaraokeLayout) "2-Line Stage" else "4-Line Stage",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.activeFill,
                                        selectedLabelColor = Color(0xFF040914)
                                    )
                                )
                                IconButton(onClick = onOpenLatencyCalibration) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Audio & Bluetooth Latency Calibration",
                                        tint = Color(0xFF00FF9D)
                                    )
                                }
                                IconButton(onClick = onOpenSyncEditor) {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = "Lyrics Sync Editor",
                                        tint = Color(0xFFFFD600)
                                    )
                                }
                                IconButton(onClick = onToggleFullScreen) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Full Screen Stage",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        SyllableKaraokeStage(
                    lyricsEnabled = library.lyricsEnabled, countdownEnabled = library.countdownEnabled,
                    wipeEnabled = library.lyricWipeEnabled, singerInfoEnabled = library.singerInfoEnabled,
                            playback = playback,
                            palette = palette,
                            fontScale = library.lyricFontSizeScale,
                            karaokeMode = library.karaokeMode,
                            activeSinger = library.currentQueueRow?.singer ?: library.activeSingerName,
                            duetPartner = library.currentQueueRow?.secondSinger ?: library.activeDuetPartner,
                            onSkipInterlude = onSkipInterlude,
                            twoLineMode = library.twoLineKaraokeLayout,
                            displayMode = library.lyricDisplayMode,
                            fontFamilyChoice = library.lyricFontFamily,
                            fontWeightChoice = library.lyricFontWeight,
                            letterSpacingSp = library.lyricLetterSpacing,
                            lineSpacingDp = library.lyricLineSpacing,
                            textAlignment = library.lyricTextAlignment,
                            stagePosition = library.lyricStagePosition,
                            outlineThicknessDp = library.lyricOutlineThickness,
                            shadowIntensity = library.lyricShadowIntensity,
                            onOpenSyncEditor = onOpenSyncEditor
                        )

                        Spacer(Modifier.height(8.dp))

                        // Timeline Seek Bar
                        val duration = playback.durationMs.coerceAtLeast(1L)
                        val progress = if (playback.durationMs > 0L) {
                            (playback.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = progress,
                            enabled = playback.file != null && playback.durationMs > 0L,
                            onValueChange = { fraction ->
                                onSeek((fraction * duration).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = palette.activeFill,
                                activeTrackColor = palette.activeFill
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(playback.positionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Key ${formatSigned(library.keyTranspose)} • Tempo ${(library.tempoMultiplier * 100).roundToInt()}% • Sync ${if (library.latencyCalibration.totalEffectiveOffsetMs >= 0) "+" else ""}${library.latencyCalibration.totalEffectiveOffsetMs}ms",
                                color = palette.activeGlow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                formatTime(playback.durationMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Live Pitch Guide & Microphone Scoring Bar
        if (library.liveScoringEnabled || library.karaokeMode == "PRACTICE") {
            item {
                LivePitchScoringBar(
                    scoreState = library.liveScoreState,
                    primaryColor = palette.activeFill,
                    accentColor = Color(0xFFFF2A85)
                )
            }
        }

        if (library.error != null || playback.error != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3B1222),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = library.error ?: playback.error.orEmpty(),
                        color = Color(0xFFFFCDD2),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Primary Transport & Stage Actions Deck
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0E152B),
                border = BorderStroke(1.dp, Color(0xFF22325C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTogglePlay,
                            enabled = playback.file != null || library.queue.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00F0FF),
                                contentColor = Color(0xFF040914)
                            )
                        ) {
                            Icon(
                                if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (playback.playing) "Pause" else "Play", fontWeight = FontWeight.Black)
                        }

                        FilledTonalButton(
                            onClick = onRestart,
                            enabled = playback.file != null
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Restart")
                        }

                        FilledTonalButton(
                            onClick = onNextSong,
                            enabled = library.queue.isNotEmpty()
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Next (${library.queue.size})")
                        }

                        FilledTonalButton(onClick = onToggleMelody) {
                            Icon(
                                if (library.melodyEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = if (library.melodyEnabled) Color(0xFF00FF9D) else Color.Gray
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (library.melodyEnabled) "Melody ON" else "Melody OFF")
                        }

                        FilledTonalButton(onClick = onOpenMixer) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color(0xFFFFD600))
                            Spacer(Modifier.width(4.dp))
                            Text("Audio Mixer")
                        }

                        FilledTonalButton(onClick = onOpenSyncEditor) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF00F0FF))
                            Spacer(Modifier.width(4.dp))
                            Text("Lyrics Sync Studio")
                        }

                        FilledTonalButton(onClick = onOpenLatencyCalibration) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00FF9D))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Calibration")
                        }

                        FilledTonalButton(onClick = onToggleMicScoring) {
                            Icon(
                                if (library.liveScoringEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (library.liveScoringEnabled) Color(0xFFFF2A85) else Color.Gray
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (library.liveScoringEnabled) "Mic Score ON" else "Mic Score OFF")
                        }

                        if (playback.file == null) {
                            OutlinedButton(onClick = onBrowseSongs) {
                                Text("Browse Songs")
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Real-Time Lyrics Sync Offset & Bluetooth Latency Nudge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LYRICS SYNC TIMING OFFSET", color = Color(0xFF00FF9D), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(
                                "${playback.syncSourceType.badgeText} • Total ${if (library.latencyCalibration.totalEffectiveOffsetMs >= 0) "+" else ""}${library.latencyCalibration.totalEffectiveOffsetMs} ms${if (library.latencyCalibration.bluetoothOutputDetected) " (BT)" else ""}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { onNudgePerSongLatency(-50L) }) {
                                Text("-50ms", fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = onOpenLatencyCalibration) {
                                Text("Calibrate")
                            }
                            FilledTonalButton(onClick = { onNudgePerSongLatency(50L) }) {
                                Text("+50ms", fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // Real-Time Key Transpose (-12 to +12 Semitones)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("KEY TRANSPOSE (PITCH)", color = Color(0xFF00F0FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(
                                "${formatSigned(library.keyTranspose)} Semitones",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { onChangeKey(library.keyTranspose - 1) }) {
                                Text("♭ -1", fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { onChangeKey(0) }) {
                                Text("0 Natural")
                            }
                            FilledTonalButton(onClick = { onChangeKey(library.keyTranspose + 1) }) {
                                Text("♯ +1", fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // Real-Time Tempo Control (50% to 150%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TEMPO SPEED (SYNC-LOCKED)", color = Color(0xFFFF2A85), fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(
                                "${(library.tempoMultiplier * 100).roundToInt()}% (${playback.bpm} BPM)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { onChangeTempo(library.tempoMultiplier - 0.05f) }) {
                                Text("-5%", fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { onChangeTempo(1.0f) }) {
                                Text("100%")
                            }
                            FilledTonalButton(onClick = { onChangeTempo(library.tempoMultiplier + 0.05f) }) {
                                Text("+5%", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageBackdropLayer(
    backgroundUri: String?,
    backgroundIsImage: Boolean = false,
    positionMs: Long = -1,
    visualizerStyle: String,
    channelLevels: List<Float>,
    playing: Boolean,
    palette: com.jktv.offline.ui.LyricColorPalette,
    modifier: Modifier = Modifier,
    brightness: Float = 1.0f,
    scrimAlpha: Float = 0.4f,
    visualizerSensitivity: Float = 1.0f,
    visualizerSpeed: Float = 1.0f,
    visualizerBrightness: Float = 1.0f,
    lyricsSafeZone: Boolean = true,
    glowStrength: Float = 1.0f,
    performanceMode: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!backgroundUri.isNullOrBlank() && backgroundIsImage) {
            AndroidView(factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = brightness.coerceIn(0.1f, 1f) },
                update = { if (it.tag != backgroundUri) { it.tag = backgroundUri; it.setImageURI(Uri.parse(backgroundUri)) } })
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 0.95f))))
        } else if (!backgroundUri.isNullOrBlank()) {
            com.jktv.offline.cast.TvVideo(backgroundUri, positionMs, playing, brightness)
            // Lyrics Scrim Dimming layer for crystal-clear readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 0.95f)))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF17224D),
                                Color(0xFF0A0F24),
                                Color(0xFF050712)
                            )
                        )
                    )
            )
        }

        // Stage Visualizer is disabled via STAGE_VISUALIZER_ENABLED (code kept intact).
        if (STAGE_VISUALIZER_ENABLED) {
            NeonStageVisualizer(
                style = visualizerStyle,
                channelLevels = channelLevels,
                playing = playing,
                primaryColor = palette.activeFill,
                secondaryColor = Color(0xFFFF2A85),
                sensitivity = visualizerSensitivity,
                speedMultiplier = visualizerSpeed,
                brightness = visualizerBrightness,
                lyricsSafeZone = lyricsSafeZone,
                glowStrength = glowStrength,
                performanceMode = performanceMode,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private enum class SettingsCategory(val label: String, val icon: ImageVector) {
    LYRICS("Lyrics & Sync", Icons.Default.MusicNote),
    TYPOGRAPHY("Typography", Icons.Default.Tune),
    PALETTES("Wipe Palettes", Icons.Default.AutoAwesome),
    THEME("Design & Colors", Icons.Default.Palette),
    AUDIO("SoundFont & Audio", Icons.Default.Equalizer),
    BACKGROUND("Background Media", Icons.Default.VideoFile),
    VISUALIZER("Stage Visualizer", Icons.Default.Album),
    AUTOMATION("Automation & Setup", Icons.Default.Settings)
}

@Composable
private fun LiveThemeInteractivePreview(
    designStyleId: String,
    colorStyleId: String,
    customPrimary: String,
    customSecondary: String
) {
    val design = remember(designStyleId) { AppThemeStyles.resolveDesignStyle(designStyleId) }
    val color = remember(colorStyleId, customPrimary, customSecondary) {
        AppThemeStyles.resolveColorStyle(colorStyleId, customPrimary, customSecondary)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(design.background)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color.primary)
                    )
                    Text(
                        text = design.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.primary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, color.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = color.label,
                        color = color.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = design.card,
                border = BorderStroke(1.dp, design.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sample Song Title",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Stage Artist • 4:15",
                            color = Color(0xFF9FB2D9),
                            fontSize = 9.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.primary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Sing Now",
                            color = Color(0xFF040711),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, color.primary),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Active Button", color = color.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = design.surface,
                    border = BorderStroke(1.dp, design.border),
                    modifier = Modifier.weight(1f).height(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Secondary Card", color = Color(0xFFB5C7E8), fontSize = 10.sp, fontWeight = FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAudioAuditionPreview(
    library: LibraryUiState,
    onPlaySample: (String, String) -> Unit,
    onStopSample: () -> Unit
) {
    val activeFontName = library.soundFonts.firstOrNull { it.path == library.selectedSoundFontPath }?.name ?: "Default General MIDI SF2"
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                Text(activeFontName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (library.isSoundFontPreviewing) "▶ Synthesizing ${library.previewInstrument} sample live..." else "Tap any instrument to test FluidSynth audio",
                    color = if (library.isSoundFontPreviewing) primary else Color(0xFF9FB2D9),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (library.isSoundFontPreviewing) {
                IconButton(onClick = onStopSample, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Pause, contentDescription = "Stop Sample", tint = Color(0xFFFF2A85))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val instruments = listOf(
                "PIANO" to "🎹 Piano",
                "GUITAR" to "🎸 Guitar",
                "STRINGS" to "🎻 Strings",
                "BRASS" to "🎺 Brass",
                "DRUMS" to "🥁 Drums"
            )
            instruments.forEach { (instId, label) ->
                val active = library.isSoundFontPreviewing && library.previewInstrument == instId
                Surface(
                    onClick = {
                        val path = library.selectedSoundFontPath ?: library.soundFonts.firstOrNull()?.path.orEmpty()
                        onPlaySample(path, instId)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) primary else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (active) primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = if (active) Color(0xFF040711) else Color.White,
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAutomationStatusPreview(library: LibraryUiState) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JKTV Offline System Status", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (library.installed) Color(0xFF00FF9D).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f)
            ) {
                Text(
                    text = if (library.installed) "● 100% OFFLINE READY" else "● SETUP NEEDED",
                    color = if (library.installed) Color(0xFF00FF9D) else Color(0xFFFFB300),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("SONGS INDEXED", fontSize = 9.sp, color = primary, fontWeight = FontWeight.Bold)
                    Text("${library.songCount}", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("LATENCY OFFSET", fontSize = 9.sp, color = Color(0xFFFF2A85), fontWeight = FontWeight.Bold)
                    Text("${library.latencyCalibration.totalEffectiveOffsetMs} ms", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("APP VERSION", fontSize = 9.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                    Text("v${BuildConfig.VERSION_NAME}", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun StickyLiveSettingsPreview(
    activeCategory: SettingsCategory,
    library: LibraryUiState,
    activePalette: com.jktv.offline.ui.LyricColorPalette,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlaySampleSoundFont: (String, String) -> Unit,
    onStopSampleSoundFont: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            activeCategory.icon,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = when (activeCategory) {
                            SettingsCategory.LYRICS -> "LIVE KARAOKE LYRICS PREVIEW"
                            SettingsCategory.TYPOGRAPHY -> "LIVE TYPOGRAPHY & OUTLINE PREVIEW"
                            SettingsCategory.PALETTES -> "LIVE SYLLABLE WIPE PALETTE"
                            SettingsCategory.THEME -> "LIVE DESIGN & COLOR STYLE PREVIEW"
                            SettingsCategory.AUDIO -> "LIVE SOUNDFONT INSTRUMENT AUDITION"
                            SettingsCategory.BACKGROUND -> "LIVE VIDEO & SCRIM PREVIEW"
                            SettingsCategory.VISUALIZER -> "LIVE STAGE VISUALIZER DEMO"
                            SettingsCategory.AUTOMATION -> "KARAOKE SYSTEM STATUS"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = primaryColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isExpanded) "LIVE PREVIEW" else "TAP TO EXPAND",
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse Preview" else "Expand Preview",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF04060E))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    when (activeCategory) {
                        SettingsCategory.LYRICS,
                        SettingsCategory.TYPOGRAPHY,
                        SettingsCategory.PALETTES -> {
                            LiveLyricsPreviewBox(
                                palette = activePalette,
                                fontScale = library.lyricFontSizeScale,
                                displayMode = library.lyricDisplayMode,
                                fontFamilyChoice = library.lyricFontFamily,
                                fontWeightChoice = library.lyricFontWeight,
                                letterSpacingSp = library.lyricLetterSpacing,
                                lineSpacingDp = library.lyricLineSpacing,
                                textAlignment = library.lyricTextAlignment,
                                stagePosition = library.lyricStagePosition,
                                outlineThicknessDp = library.lyricOutlineThickness,
                                shadowIntensity = library.lyricShadowIntensity,
                                visualizerStyle = library.visualizerStyle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        SettingsCategory.THEME -> {
                            LiveThemeInteractivePreview(
                                designStyleId = library.designStyle,
                                colorStyleId = library.colorStyle,
                                customPrimary = library.customPrimaryColorHex,
                                customSecondary = library.customSecondaryColorHex
                            )
                        }
                        SettingsCategory.VISUALIZER -> {
                            val mockLevels = remember { listOf(0.45f, 0.72f, 0.90f, 0.65f, 0.38f, 0.85f, 0.95f, 0.50f, 0.80f, 0.60f, 0.75f, 0.40f, 0.88f, 0.70f, 0.55f, 0.68f) }
                            Box(modifier = Modifier.fillMaxSize()) {
                                NeonStageVisualizer(
                                    style = library.visualizerStyle,
                                    channelLevels = mockLevels,
                                    playing = true,
                                    primaryColor = activePalette.activeFill,
                                    secondaryColor = activePalette.activeGlow,
                                    sensitivity = library.visualizerSensitivity,
                                    speedMultiplier = library.visualizerSpeed,
                                    brightness = library.visualizerBrightness,
                                    lyricsSafeZone = library.visualizerLyricsSafeZone,
                                    glowStrength = library.visualizerGlowStrength,
                                    performanceMode = library.visualizerPerformanceMode,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Demo safe-zone lyric line in preview
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, activePalette.activeFill.copy(alpha = 0.5f)),
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "♪ Lyrics Safe Zone • Pure & Distraction-Free ♪",
                                        color = activePalette.activeFill,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        SettingsCategory.BACKGROUND -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                StageBackdropLayer(
                                    backgroundUri = library.backgroundVideoUri,
                backgroundIsImage = library.backgroundIsImage,
                                    visualizerStyle = library.visualizerStyle,
                                    channelLevels = remember { listOf(0.4f, 0.8f, 0.6f, 0.9f) },
                                    playing = true,
                                    palette = activePalette,
                                    brightness = library.backgroundBrightness,
                                    scrimAlpha = library.lyricsScrimAlpha,
                                    visualizerSensitivity = library.visualizerSensitivity,
                                    visualizerSpeed = library.visualizerSpeed,
                                    visualizerBrightness = library.visualizerBrightness,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                                ) {
                                    Text(
                                        text = "♪ Fly me to the moon and let me play ♪",
                                        color = activePalette.activeFill,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        SettingsCategory.AUDIO -> {
                            LiveAudioAuditionPreview(
                                library = library,
                                onPlaySample = onPlaySampleSoundFont,
                                onStopSample = onStopSampleSoundFont
                            )
                        }
                        SettingsCategory.AUTOMATION -> {
                            LiveAutomationStatusPreview(library = library)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupAndSettingsScreen(
    library: LibraryUiState,
    onCheckNewLibrary: () -> Unit,
    onDownloadLibraryUpdates: () -> Unit,
    onStartSetup: () -> Unit,
    onPauseSetup: () -> Unit,
    onSelectSoundFont: (String) -> Unit,
    onImportSoundFont: () -> Unit,
    onImportCustomSong: () -> Unit,
    onSelectBackground: (String) -> Unit,
    onChooseDeviceVideo: () -> Unit,
    onClearBackground: () -> Unit,
    onChangeLyricFontScale: (Float) -> Unit,
    onChangeLyricPalette: (String) -> Unit,
    onChangeVisualizerStyle: (String) -> Unit,
    onToggleAutoPlayNext: (Boolean) -> Unit,
    onToggleVoiceAnnouncer: (Boolean) -> Unit,
    onToggleMicScoring: (Boolean) -> Unit,
    onOpenRemoteQr: () -> Unit,
    onOpenMixer: () -> Unit,
    onCheckUpdates: () -> Unit,
    onPlaySampleSoundFont: (String, String) -> Unit,
    onStopSampleSoundFont: () -> Unit,
    onChangeFontFamily: (String) -> Unit,
    onChangeFontWeight: (String) -> Unit,
    onChangeLetterSpacing: (Float) -> Unit,
    onChangeLineSpacing: (Float) -> Unit,
    onChangeTextAlignment: (String) -> Unit,
    onChangeStagePosition: (String) -> Unit,
    onChangeOutlineThickness: (Float) -> Unit,
    onChangeShadowIntensity: (Float) -> Unit,
    onChangeDisplayMode: (String) -> Unit,
    onChangeStageElement: (String, Boolean) -> Unit,
    onChangePhraseGrouping: (String) -> Unit,
    onChangeBackgroundBrightness: (Float) -> Unit,
    onChangeLyricsScrimAlpha: (Float) -> Unit,
    onChangeVisualizerSensitivity: (Float) -> Unit,
    onChangeVisualizerSpeed: (Float) -> Unit,
    onChangeVisualizerBrightness: (Float) -> Unit,
    onChangeVisualizerIntensityPreset: (String) -> Unit,
    onChangeVisualizerSafeZone: (Boolean) -> Unit,
    onChangeVisualizerGlowStrength: (Float) -> Unit,
    onChangeVisualizerPerformanceMode: (Boolean) -> Unit,
    onChangeCustomPaletteColors: (String, String, String, String) -> Unit,
    onResetLyricsTypography: () -> Unit,
    onResetWipePalettes: () -> Unit,
    onResetVisualizer: () -> Unit,
    onResetBackgroundVideo: () -> Unit,
    onOpenSyncEditor: () -> Unit,
    onSelectDesignStyle: (String) -> Unit,
    onSelectColorStyle: (String) -> Unit,
    onSetCustomThemeColors: (String, String) -> Unit,
    onResetThemeDefaults: () -> Unit
) {
    var activeCategory by rememberSaveable { mutableStateOf(SettingsCategory.LYRICS) }

    val basePalette = resolveLyricPalette(library.lyricPaletteName)
    val activePalette = if (library.lyricPaletteName == "CUSTOM_PALETTE") {
        basePalette.copy(
            unsungFill = parseHexColor(library.customUnsungFillHex, basePalette.unsungFill),
            activeFill = parseHexColor(library.customActiveFillHex, basePalette.activeFill),
            activeGlow = parseHexColor(library.customActiveGlowHex, basePalette.activeGlow),
            nextLineFill = parseHexColor(library.customNextLineFillHex, basePalette.nextLineFill)
        )
    } else basePalette

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky Top Tab Header (Horizontal Scroll)
        Surface(
            color = Color(0xFF090F20),
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, Color(0xFF1E2D56))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsCategory.entries
                    .filter { STAGE_VISUALIZER_ENABLED || it != SettingsCategory.VISUALIZER }
                    .forEach { cat ->
                    val selected = activeCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { activeCategory = cat },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    cat.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selected) Color(0xFF040914) else Color(0xFF00F0FF)
                                )
                                Text(
                                    cat.label,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00F0FF),
                            selectedLabelColor = Color(0xFF040914)
                        )
                    )
                }
            }
        }

        var previewExpanded by rememberSaveable { mutableStateOf(true) }
        StickyLiveSettingsPreview(
            activeCategory = activeCategory,
            library = library,
            activePalette = activePalette,
            isExpanded = previewExpanded,
            onToggleExpand = { previewExpanded = !previewExpanded },
            onPlaySampleSoundFont = onPlaySampleSoundFont,
            onStopSampleSoundFont = onStopSampleSoundFont
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (library.settingsMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF12284C),
                        border = BorderStroke(1.dp, Color(0xFF00F0FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = library.settingsMessage,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            when (activeCategory) {
                SettingsCategory.LYRICS -> {
                    item {
                        SettingsCard(title = "STAGE ELEMENTS", accent = MaterialTheme.colorScheme.primary) {
                            listOf(
                                Triple("lyricsEnabled", "Show lyrics", library.lyricsEnabled),
                                Triple("lyricWipeEnabled", "Syllable highlighting", library.lyricWipeEnabled),
                                Triple("countdownEnabled", "Show countdown", library.countdownEnabled),
                                Triple("stageGuestQrEnabled", "Show TV guest QR code", library.stageGuestQrEnabled),
                                Triple("songInfoEnabled", "Show song information", library.songInfoEnabled),
                                Triple("singerInfoEnabled", "Show singer information", library.singerInfoEnabled)
                            ).forEach { (name, label, enabled) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, modifier = Modifier.weight(1f))
                                    Switch(checked = enabled, onCheckedChange = { onChangeStageElement(name, it) })
                                }
                            }
                        }
                    }

                    // TAB 0: LYRICS & SYNCHRONIZATION
                    item {
                        SettingsCard(title = "KARAOKE DISPLAY MODE", accent = Color(0xFF00FF9D)) {
                            Text("Choose your stage lyric presentation mode:", fontSize = 12.sp, color = Color(0xFFB5C7E8))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val modes = listOf(
                                    "TWO_LINE" to "2-Line Commercial Karaoke (Recommended)",
                                    "ONE_LINE" to "1-Line Big Stage",
                                    "FOUR_LINE" to "4-Line Classic Screen"
                                )
                                modes.forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricDisplayMode == id,
                                        onClick = { onChangeDisplayMode(id) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "INTELLIGENT PHRASE GROUPING", accent = Color(0xFFFFD600)) {
                            Text(
                                "Commercial karaoke systems show complete musical phrases instead of choppy 1-word fragments. Groups short syllables naturally by musical breath pauses.",
                                fontSize = 12.sp,
                                color = Color(0xFFB5C7E8)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val groupings = listOf(
                                    "NATURAL_FULL" to "Full Musical Phrases (Recommended)",
                                    "RAW_MIDI" to "Raw Hardware LCD Fragments"
                                )
                                groupings.forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricPhraseGrouping == id,
                                        onClick = { onChangePhraseGrouping(id) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "AUDIO & BLUETOOTH LATENCY SYNCHRONIZATION", accent = Color(0xFFFF2A85)) {
                            Text(
                                "Total Effective Offset: ${if (library.latencyCalibration.totalEffectiveOffsetMs >= 0) "+" else ""}${library.latencyCalibration.totalEffectiveOffsetMs} ms (Global: ${library.latencyCalibration.globalOffsetMs}ms, BT Compensation: ${library.latencyCalibration.bluetoothOffsetMs}ms)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenSyncEditor,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF2A85),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.EditNote, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Lyrics Sync Studio", fontWeight = FontWeight.Black)
                                }
                                OutlinedButton(onClick = onResetLyricsTypography) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset Defaults")
                                }
                            }
                        }
                    }
                }

                SettingsCategory.TYPOGRAPHY -> {
                    // TAB 1: TYPOGRAPHY & LAYOUT
                    item {
                        SettingsCard(title = "FONT SIZE & FAMILY", accent = Color(0xFFFFD600)) {
                            Text(
                                "Font Size Scale: ${(library.lyricFontSizeScale * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricFontSizeScale,
                                onValueChange = onChangeLyricFontScale,
                                valueRange = 0.75f..1.85f
                            )

                            Text("Font Family:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "DEFAULT" to "Default System",
                                    "SANS_SERIF" to "Modern Sans-Serif",
                                    "CONDENSED" to "Commercial Condensed",
                                    "SERIF" to "Classic Serif",
                                    "MONOSPACE" to "Retro Monospace",
                                    "CASUAL" to "Casual Script",
                                    "CURSIVE" to "Fluid Cursive"
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricFontFamily == id,
                                        onClick = { onChangeFontFamily(id) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (library.lyricFontFamily == id) FontWeight.Black else FontWeight.Normal) }
                                    )
                                }
                            }

                            Text("Font Weight (Boldness):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "BLACK" to "Heavy Black (900)",
                                    "EXTRA_BOLD" to "Extra Bold (800)",
                                    "BOLD" to "Bold (700)",
                                    "SEMI_BOLD" to "Semi-Bold (600)",
                                    "MEDIUM" to "Medium (500)",
                                    "NORMAL" to "Regular (400)",
                                    "LIGHT" to "Light (300)"
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricFontWeight == id,
                                        onClick = { onChangeFontWeight(id) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (library.lyricFontWeight == id) FontWeight.Black else FontWeight.Normal) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "SPACING & STAGE POSITION", accent = Color(0xFF00FF9D)) {
                            Text(
                                "Letter Spacing: ${"%.1f".format(library.lyricLetterSpacing)} sp",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricLetterSpacing,
                                onValueChange = onChangeLetterSpacing,
                                valueRange = -1.5f..4.0f
                            )

                            Text(
                                "Line Spacing: ${library.lyricLineSpacing.roundToInt()} dp",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricLineSpacing,
                                onValueChange = onChangeLineSpacing,
                                valueRange = 6f..32f
                            )

                            Text("Text Alignment:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "CENTER" to "Centered Karaoke",
                                    "LEFT" to "Left Aligned"
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricTextAlignment == id,
                                        onClick = { onChangeTextAlignment(id) },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Text("Lyrics Screen Position:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "CENTER" to "Center Stage",
                                    "BOTTOM" to "Bottom Subtitles",
                                    "TOP" to "Top Screen"
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.lyricStagePosition == id,
                                        onClick = { onChangeStagePosition(id) },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "OUTLINE & SHADOW EFFECTS", accent = Color(0xFFFF2A85)) {
                            Text(
                                "High-Contrast Outline Thickness: ${"%.1f".format(library.lyricOutlineThickness)} dp",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricOutlineThickness,
                                onValueChange = onChangeOutlineThickness,
                                valueRange = 0.0f..4.0f
                            )

                            Text(
                                "Glow & Shadow Intensity: ${"%.1f".format(library.lyricShadowIntensity)}x",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricShadowIntensity,
                                onValueChange = onChangeShadowIntensity,
                                valueRange = 0.0f..2.0f
                            )

                            OutlinedButton(onClick = onResetLyricsTypography) {
                                Icon(Icons.Default.Replay, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Typography to Defaults")
                            }
                        }
                    }
                }

                SettingsCategory.PALETTES -> {
                    // TAB 2: SYLLABLE WIPE PALETTES
                    item {
                        SettingsCard(title = "15 PROFESSIONAL KARAOKE COLOR PALETTES", accent = Color(0xFFFFD600)) {
                            Text("Select an authentic commercial karaoke syllable wiping color scheme:", fontSize = 12.sp, color = Color(0xFFB5C7E8))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LyricPalettes.forEach { pal ->
                                    val isSelected = library.lyricPaletteName == pal.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onChangeLyricPalette(pal.id) },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(pal.activeFill)
                                                )
                                                Text(pal.label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal)
                                            }
                                        }
                                    )
                                }
                            }

                            if (library.lyricPaletteName == "CUSTOM_PALETTE") {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                                Text("Configure Custom Palette Hex Colors:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                var activeFillInput by remember { mutableStateOf(library.customActiveFillHex) }
                                var activeGlowInput by remember { mutableStateOf(library.customActiveGlowHex) }
                                var unsungFillInput by remember { mutableStateOf(library.customUnsungFillHex) }
                                var nextLineInput by remember { mutableStateOf(library.customNextLineFillHex) }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = activeFillInput,
                                        onValueChange = {
                                            activeFillInput = it
                                            onChangeCustomPaletteColors(activeFillInput, activeGlowInput, unsungFillInput, nextLineInput)
                                        },
                                        label = { Text("Active Fill Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = activeGlowInput,
                                        onValueChange = {
                                            activeGlowInput = it
                                            onChangeCustomPaletteColors(activeFillInput, activeGlowInput, unsungFillInput, nextLineInput)
                                        },
                                        label = { Text("Active Glow Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = unsungFillInput,
                                        onValueChange = {
                                            unsungFillInput = it
                                            onChangeCustomPaletteColors(activeFillInput, activeGlowInput, unsungFillInput, nextLineInput)
                                        },
                                        label = { Text("Unsung Text Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = nextLineInput,
                                        onValueChange = {
                                            nextLineInput = it
                                            onChangeCustomPaletteColors(activeFillInput, activeGlowInput, unsungFillInput, nextLineInput)
                                        },
                                        label = { Text("Next Line Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            OutlinedButton(onClick = onResetWipePalettes) {
                                Icon(Icons.Default.Replay, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Palettes Default")
                            }
                        }
                    }
                }

                SettingsCategory.THEME -> {
                    // TAB: DESIGN STYLE & COLOR STYLE
                    item {
                        SettingsCard(title = "DESIGN STYLE (APPEARANCE)", accent = MaterialTheme.colorScheme.primary) {
                            Text(
                                "Select a clean, AMOLED-optimized interface design style:",
                                fontSize = 12.sp,
                                color = Color(0xFFB5C7E8)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppThemeStyles.DesignStyles.forEach { style ->
                                    val selected = library.designStyle == style.id
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    Surface(
                                        onClick = { onSelectDesignStyle(style.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) primaryColor else style.border
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = style.label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (selected) primaryColor else Color.White
                                                )
                                                Text(
                                                    text = style.description,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF9FB2D9)
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(style.background).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(style.surface).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(style.card).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "COLOR STYLE (ACCENT PALETTES)", accent = MaterialTheme.colorScheme.secondary) {
                            Text(
                                "Choose your dynamic accent highlight colors:",
                                fontSize = 12.sp,
                                color = Color(0xFFB5C7E8)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppThemeStyles.ColorStyles.forEach { color ->
                                    val selected = library.colorStyle == color.id
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectColorStyle(color.id) },
                                        leadingIcon = {
                                            Row(
                                                modifier = Modifier.padding(end = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color.primary))
                                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color.secondary))
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = color.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.Black else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }

                            if (library.colorStyle == "CUSTOM") {
                                Spacer(Modifier.height(8.dp))
                                Text("Custom Color Hex Codes:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    var customPrimaryText by rememberSaveable(library.customPrimaryColorHex) { mutableStateOf(library.customPrimaryColorHex) }
                                    var customSecondaryText by rememberSaveable(library.customSecondaryColorHex) { mutableStateOf(library.customSecondaryColorHex) }

                                    OutlinedTextField(
                                        value = customPrimaryText,
                                        onValueChange = {
                                            customPrimaryText = it
                                            if (it.matches(Regex("^#?[0-9a-fA-F]{6}$"))) {
                                                onSetCustomThemeColors(if (it.startsWith("#")) it else "#$it", library.customSecondaryColorHex)
                                            }
                                        },
                                        label = { Text("Primary Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = customSecondaryText,
                                        onValueChange = {
                                            customSecondaryText = it
                                            if (it.matches(Regex("^#?[0-9a-fA-F]{6}$"))) {
                                                onSetCustomThemeColors(library.customPrimaryColorHex, if (it.startsWith("#")) it else "#$it")
                                            }
                                        },
                                        label = { Text("Secondary Hex") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onResetThemeDefaults,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset to JKTV Signature Defaults")
                            }
                        }
                    }
                }

                SettingsCategory.AUDIO -> {
                    // TAB 3: SOUNDFONT & AUDIO
                    item {
                        SettingsCard(title = "ACTIVE SOUNDFONT & LIVE AUDIO AUDITION", accent = Color(0xFFFFD600)) {
                            Text(
                                text = "Current SoundFont: ${library.soundFonts.firstOrNull { it.path == library.selectedSoundFontPath }?.name ?: "Default General MIDI SF2"}",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                "Audition real SoundFont instrument samples generated live through the FluidSynth MIDI synthesizer:",
                                fontSize = 12.sp,
                                color = Color(0xFFB5C7E8)
                            )

                            // 5 Sample Instrument Chips
                            Text("Sample Instrument:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            val instruments = listOf(
                                "PIANO" to "🎹 Grand Piano",
                                "GUITAR" to "🎸 Acoustic Guitar",
                                "STRINGS" to "🎻 Strings Ensemble",
                                "BRASS" to "🎺 Brass Section",
                                "DRUMS" to "🥁 Standard Drums"
                            )
                            var chosenInstrument by rememberSaveable { mutableStateOf("PIANO") }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                instruments.forEach { (id, label) ->
                                    FilterChip(
                                        selected = chosenInstrument == id,
                                        onClick = {
                                            chosenInstrument = id
                                            if (library.isSoundFontPreviewing) {
                                                onPlaySampleSoundFont(library.selectedSoundFontPath.orEmpty(), id)
                                            }
                                        },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }

                            // Play / Stop Audition Button
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (library.isSoundFontPreviewing) {
                                            onStopSampleSoundFont()
                                        } else {
                                            onPlaySampleSoundFont(library.selectedSoundFontPath.orEmpty(), chosenInstrument)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (library.isSoundFontPreviewing) Color(0xFFFF2A85) else Color(0xFFFFD600),
                                        contentColor = Color(0xFF040914)
                                    )
                                ) {
                                    if (library.soundFontLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF040914), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Initializing…", fontWeight = FontWeight.Black)
                                    } else if (library.isSoundFontPreviewing) {
                                        Icon(Icons.Default.Pause, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Stop Audition Sample", fontWeight = FontWeight.Black)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play Live Audio Sample", fontWeight = FontWeight.Black)
                                    }
                                }

                                OutlinedButton(onClick = onOpenMixer) {
                                    Icon(Icons.Default.Equalizer, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Studio Mixer")
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "INSTALLED SF2 SOUNDFONT BANKS", accent = Color(0xFF00FF9D)) {
                            if (library.soundFonts.isEmpty()) {
                                Text("No SF2 SoundFonts installed yet. Download the offline library or import a .sf2 file.", fontSize = 12.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    library.soundFonts.forEach { sf ->
                                        val selected = sf.path == library.selectedSoundFontPath
                                        Surface(
                                            onClick = {
                                                onSelectSoundFont(sf.path)
                                                onPlaySampleSoundFont(sf.path, "PIANO")
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) Color(0xFF1B2A52) else Color(0xFF0B1124),
                                            border = BorderStroke(1.dp, if (selected) Color(0xFFFFD600) else Color(0xFF1E2D56)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sf.name, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text(
                                                        if (selected) "Active Synthesizer SoundFont • Tap to Audition" else "Tap to activate & audition",
                                                        fontSize = 11.sp,
                                                        color = if (selected) Color(0xFFFFD600) else Color(0xFF9FB2D9)
                                                    )
                                                }
                                                if (selected) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFFFD600))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            FilledTonalButton(onClick = onImportSoundFont) {
                                Text("Import Custom .SF2 SoundFont")
                            }
                        }
                    }
                }

                SettingsCategory.BACKGROUND -> {
                    // TAB 4: BACKGROUND VIDEO
                    item {
                        SettingsCard(title = "VIDEO BRIGHTNESS & LYRICS SCRIM DIMMING", accent = Color(0xFF00F0FF)) {
                            Text(
                                "Background Video Brightness: ${(library.backgroundBrightness * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.backgroundBrightness,
                                onValueChange = onChangeBackgroundBrightness,
                                valueRange = 0.1f..1.0f
                            )

                            Text(
                                "Lyrics Background Dimming (Scrim): ${(library.lyricsScrimAlpha * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.lyricsScrimAlpha,
                                onValueChange = onChangeLyricsScrimAlpha,
                                valueRange = 0.0f..0.9f
                            )
                        }
                    }

                    item {
                        SettingsCard(title = "BACKGROUND SELECTION & VIDEO IMPORT", accent = Color(0xFFFFD600)) {
                            Text("Select an authentic concert background or custom MP4 video:", fontSize = 12.sp, color = Color(0xFFB5C7E8))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = library.backgroundVideoUri == null,
                                    onClick = onClearBackground,
                                    label = { Text("Procedural Neon Stage (Default)", fontSize = 12.sp) }
                                )
                                library.backgroundVideos.forEach { bg ->
                                    val selected = library.backgroundVideoUri?.endsWith(bg.name + ".mp4") == true
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectBackground(bg.path) },
                                        label = { Text(bg.name, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = onChooseDeviceVideo) {
                                    Icon(Icons.Default.VideoFile, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pick Local MP4 Video")
                                }
                                OutlinedButton(onClick = onResetBackgroundVideo) {
                                    Icon(Icons.Default.Replay, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset Video Settings")
                                }
                            }
                        }
                    }
                }

                SettingsCategory.VISUALIZER -> {
                    // TAB 5: STAGE VISUALIZER
                    item {
                        SettingsCard(title = "20 HIGH-PERFORMANCE VISUALIZER STYLES", accent = Color(0xFF00F0FF)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                VisualizerStyles.forEach { (id, label) ->
                                    FilterChip(
                                        selected = library.visualizerStyle == id,
                                        onClick = { onChangeVisualizerStyle(id) },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (library.visualizerStyle == id) FontWeight.Black else FontWeight.Normal) }
                                    )
                                }
                            }

                            Text(
                                "Audio Sensitivity: ${"%.1f".format(library.visualizerSensitivity)}x",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.visualizerSensitivity,
                                onValueChange = onChangeVisualizerSensitivity,
                                valueRange = 0.5f..2.5f
                            )

                            Text(
                                "Animation Speed: ${"%.1f".format(library.visualizerSpeed)}x",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.visualizerSpeed,
                                onValueChange = onChangeVisualizerSpeed,
                                valueRange = 0.5f..2.5f
                            )

                            Text(
                                "Visualizer Brightness: ${(library.visualizerBrightness * 100).roundToInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Slider(
                                value = library.visualizerBrightness,
                                onValueChange = onChangeVisualizerBrightness,
                                valueRange = 0.1f..1.0f
                            )

                            OutlinedButton(onClick = onResetVisualizer) {
                                Icon(Icons.Default.Replay, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Visualizer to Defaults")
                            }
                        }
                    }
                }

                SettingsCategory.AUTOMATION -> {
                    // TAB 6: AUTOMATION, SCORING & SETUP
                    item {
                        SettingsCard(title = "OFFLINE LIBRARY MANAGEMENT", accent = Color(0xFF00F0FF)) {
                            if (library.installed) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF00FF9D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Installed & Indexed: ${library.songCount} offline MIDI/KAR songs ready.",
                                        color = Color(0xFF00FF9D),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Download the offline song library and SF2 SoundFonts. Works 100% offline once downloaded.",
                                    color = Color(0xFFD5E3FF),
                                    fontSize = 13.sp
                                )
                            }

                            if (library.libraryCheckMessage != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (library.availableLibraryUpdates != null) Color(0xFFFFD600).copy(alpha = 0.15f) else Color(0xFF00FF9D).copy(alpha = 0.15f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (library.availableLibraryUpdates != null) Color(0xFFFFD600).copy(alpha = 0.4f) else Color(0xFF00FF9D).copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (library.availableLibraryUpdates != null) Icons.Default.CloudDownload else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (library.availableLibraryUpdates != null) Color(0xFFFFD600) else Color(0xFF00FF9D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = library.libraryCheckMessage,
                                            color = if (library.availableLibraryUpdates != null) Color(0xFFFFE082) else Color(0xFFA7F3D0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (library.availableLibraryUpdates != null) {
                                val diff = library.availableLibraryUpdates
                                Button(
                                    onClick = onDownloadLibraryUpdates,
                                    enabled = !library.busy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00F0FF),
                                        contentColor = Color(0xFF040914)
                                    )
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download Updates (${formatBytes(diff.totalDownloadBytes)})", fontWeight = FontWeight.Black)
                                }
                            }

                            if (library.busy || library.paused || library.progressText != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearProgressIndicator(
                                        progress = { library.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = Color(0xFF00F0FF)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = library.progressText ?: "Working…",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = "${(library.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                                            color = Color(0xFF00F0FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val statsText = buildString {
                                        if (library.downloadTotalFiles > 0) {
                                            append("Files: ${library.downloadRemainingFiles} remaining of ${library.downloadTotalFiles}")
                                        }
                                        if (library.downloadedBytes > 0L) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${formatBytes(library.downloadedBytes)} transferred")
                                        }
                                        if (library.downloadSpeedBytesPerSecond > 0L) {
                                            if (isNotEmpty()) append(" • ")
                                            append("${formatBytes(library.downloadSpeedBytesPerSecond)}/s")
                                        }
                                        library.remainingSeconds?.let { eta ->
                                            if (isNotEmpty()) append(" • ")
                                            append("ETA ${eta / 60}m ${eta % 60}s")
                                        }
                                    }
                                    if (statsText.isNotBlank()) {
                                        Text(
                                            text = statsText,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            if (library.error != null) {
                                Text(library.error, color = Color(0xFFFF6E6E), fontSize = 12.sp)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (library.installed) {
                                    Button(
                                        onClick = onCheckNewLibrary,
                                        enabled = !library.busy && !library.libraryCheckBusy,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1E2D4A),
                                            contentColor = Color(0xFF00F0FF)
                                        )
                                    ) {
                                        if (library.libraryCheckBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF00F0FF)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Checking…", fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Check New Library", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = onStartSetup,
                                        enabled = !library.busy,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00F0FF),
                                            contentColor = Color(0xFF040914)
                                        )
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            when {
                                                library.paused -> "Resume Download"
                                                else -> "Download Library"
                                            },
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                if (library.busy) {
                                    FilledTonalButton(onClick = onPauseSetup) {
                                        Text("Pause")
                                    }
                                }
                                FilledTonalButton(onClick = onImportCustomSong) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Import .MID/.KAR Song")
                                }
                            }
                        }
                    }

                    item {
                        SettingsCard(title = "SMART KARAOKE AUTOMATION & SCORING", accent = Color(0xFF00FF9D)) {
                            SettingToggleRow(
                                title = "Automatic Next-Song Playback",
                                subtitle = "Automatically advance and start the next song in the singer queue when a song finishes",
                                checked = library.autoPlayNextEnabled,
                                onCheckedChange = onToggleAutoPlayNext
                            )
                            SettingToggleRow(
                                title = "Automatic Singer Voice Announcer (Offline TTS)",
                                subtitle = "Announce the next singer's name and song title on stage transitions",
                                checked = library.voiceAnnouncerEnabled,
                                onCheckedChange = onToggleVoiceAnnouncer
                            )
                            SettingToggleRow(
                                title = "Live Microphone Pitch Scoring (YIN Engine)",
                                subtitle = "Score your singing in real time using your device microphone against the MIDI vocal melody",
                                checked = library.liveScoringEnabled,
                                onCheckedChange = onToggleMicScoring
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenRemoteQr,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00FF9D),
                                        contentColor = Color(0xFF040914)
                                    )
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Wi-Fi QR Remote Pairing", fontWeight = FontWeight.Black)
                                }
                                OutlinedButton(onClick = onCheckUpdates) {
                                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check GitHub Releases")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E152B),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFF9FB2D9), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyLibraryBanner(onOpenSetup: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF111A36),
        border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Offline Karaoke Library Not Installed Yet", fontWeight = FontWeight.Black, color = Color.White, fontSize = 17.sp)
            Text(
                "Open Setup to download the offline MIDI/KAR song packages and SF2 SoundFonts, or import your own .mid/.kar files directly.",
                color = Color(0xFFB5C7E8),
                fontSize = 13.sp
            )
            Button(onClick = onOpenSetup) {
                Text("Go to Library Setup", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: SongEntity,
    playlists: List<com.jktv.offline.data.PlaylistSummaryRow>,
    onCreateAndAdd: (String) -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1428),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = { Text("Add \"${song.title}\" to Playlist", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("New Playlist Name") }
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreateAndAdd(newName)
                                newName = ""
                            }
                        }
                    ) {
                        Text("Create")
                    }
                }
                if (playlists.isNotEmpty()) {
                    Text("Tap an existing playlist:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    playlists.forEach { pl ->
                        FilledTonalButton(
                            onClick = { onSelectPlaylist(pl.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${pl.name} (${pl.songCount} songs)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun UpdateStatusDialog(
    library: LibraryUiState,
    onDismiss: () -> Unit,
    onDownloadApk: () -> Unit,
    onInstallApk: () -> Unit,
    onUpdateLibrary: () -> Unit
) {
    val update = library.updateInfo
    val libraryUpdate = library.libraryUpdateManifest
    AlertDialog(
        onDismissRequest = { if (!library.updateDownloadBusy) onDismiss() },
        containerColor = Color(0xFF0E1428),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = {
            Text(
                when {
                    library.updateReadyPath != null -> "Verified Update Ready"
                    update != null -> "New JKTV Release Available (v${update.versionName})"
                    libraryUpdate != null -> "Song Library Update Available"
                    else -> "GitHub Release Check"
                },
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (update != null) {
                    Text(
                        "Version ${update.versionName} (build ${update.versionCode}) • " +
                            if (library.updateReadyPath != null) "SHA-256 verified" else "SHA-256 checked after download",
                        fontWeight = FontWeight.Bold, color = Color(0xFF00F0FF)
                    )
                    if (update.releaseNotes.isNotBlank()) {
                        Text(update.releaseNotes.take(360), fontSize = 12.sp, color = Color(0xFFB5C7E8))
                    }
                    Text(
                        "All downloaded songs, SF2 SoundFonts, favorites, playlists, and settings are preserved during update.",
                        fontSize = 12.sp
                    )
                }
                if (libraryUpdate != null) {
                    Text(
                        "Library package v${libraryUpdate.libraryVersion} (${libraryUpdate.packages.size} packages, ${formatBytes(library.libraryUpdateDownloadBytes ?: 0L)}) is available.",
                        fontSize = 13.sp
                    )
                }
                library.updateMessage?.let { Text(it) }
                if (library.updateDownloadBusy) {
                    LinearProgressIndicator(
                        progress = { library.updateProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            when {
                library.updateReadyPath != null -> Button(onClick = onInstallApk) { Text("Install Update") }
                update != null -> Button(onClick = onDownloadApk, enabled = !library.updateDownloadBusy) {
                    Text("Download & Verify APK")
                }
                libraryUpdate != null -> Button(onClick = onUpdateLibrary) { Text("Update Song Library") }
                else -> TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (!library.updateDownloadBusy && (update != null || libraryUpdate != null || library.updateReadyPath != null)) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatSigned(value: Int): String = if (value > 0) "+$value" else value.toString()

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val mb = safe / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

@Composable
private fun FirstTimeSetupScreen(
    library: LibraryUiState,
    onStartSetup: () -> Unit,
    onPauseSetup: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (!library.busy && !library.paused && !library.installed) {
            onStartSetup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040914))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF00F0FF).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF00F0FF).copy(alpha = 0.15f),
                border = BorderStroke(2.dp, Color(0xFF00F0FF)),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Setup",
                        tint = Color(0xFF00F0FF),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF00F0FF).copy(alpha = 0.18f)
            ) {
                Text(
                    text = "MANDATORY INITIAL SETUP",
                    color = Color(0xFF00F0FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Welcome to JKTV Karaoke",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "JKTV runs 100% offline. We are preparing and downloading the core song library and SoundFonts to your device. Once complete, you will never need internet again.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0D1527),
                border = BorderStroke(1.dp, Color(0xFF1E2D4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (library.paused) "DOWNLOAD PAUSED" else if (library.busy) "DOWNLOADING RESOURCES" else "READY TO DOWNLOAD",
                                color = if (library.paused) Color(0xFFFFB300) else Color(0xFF00F0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = library.progressText ?: "Connecting to library server…",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(library.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                            color = Color(0xFF00F0FF),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { library.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = Color(0xFF00F0FF),
                        trackColor = Color(0xFF142036)
                    )

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SetupStatColumn(
                            label = "DOWNLOAD SPEED",
                            value = if (library.downloadSpeedBytesPerSecond > 0L) "${formatBytes(library.downloadSpeedBytesPerSecond)}/s" else if (library.paused) "Paused" else "Connecting…"
                        )
                        SetupStatColumn(
                            label = "DOWNLOADED SIZE",
                            value = "${formatBytes(library.downloadedBytes)} / ${formatBytes(library.downloadBytes ?: library.requiredBytes ?: 0L)}"
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val remainingFilesText = if (library.downloadTotalFiles > 0) {
                            "${library.downloadRemainingFiles} of ${library.downloadTotalFiles} left"
                        } else "Calculating…"
                        SetupStatColumn(
                            label = "REMAINING FILES",
                            value = remainingFilesText
                        )
                        val etaText = library.remainingSeconds?.let { s ->
                            if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
                        } ?: if (library.busy) "Calculating…" else "—"
                        SetupStatColumn(
                            label = "ESTIMATED TIME",
                            value = etaText
                        )
                    }

                    if (library.error != null) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = library.error,
                                    color = Color(0xFFFFB4B4),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (library.busy) {
                            OutlinedButton(
                                onClick = onPauseSetup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFFB300)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pause Download", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onStartSetup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00F0FF),
                                    contentColor = Color(0xFF040914)
                                )
                            ) {
                                Icon(
                                    if (library.paused) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (library.paused) "Resume Download" else "Start Initial Download",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Downloads are saved safely and resume automatically if your connection drops.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SetupStatColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
