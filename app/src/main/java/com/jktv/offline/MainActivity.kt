package com.jktv.offline

import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jktv.offline.data.QueueRow
import com.jktv.offline.data.SongEntity

private val Ink = Color(0xFF12051F)
private val Surface = Color(0xFF1B0B2D)
private val Card = Color(0xFF28183B)
private val Violet = Color(0xFFA020F0)
private val Lilac = Color(0xFFB8A9C8)
private val Orange = Color(0xFFFF6A21)
private val Neon = Brush.linearGradient(listOf(Violet, Color(0xFF8B5CF6), Orange))

private enum class Page(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Songs("Songs", Icons.Rounded.LibraryMusic),
    Queue("Queue", Icons.Rounded.QueueMusic),
    Playing("Now Playing", Icons.Rounded.Equalizer),
    Settings("Settings", Icons.Rounded.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(18, 5, 31)
        window.navigationBarColor = android.graphics.Color.rgb(27, 11, 45)
        window.decorView.systemUiVisibility = 0
        setContent { JktvApp() }
    }
}

@Composable
private fun JktvApp() {
    val viewModel: KaraokeViewModel = viewModel()
    val context = LocalContext.current
    val library = viewModel.state
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setBackgroundVideo(uri)
        }
    }
    val soundFontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSoundFont(uri)
    }
    var page by rememberSaveable { mutableStateOf(Page.Home.name) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = Page.valueOf(page)
    LaunchedEffect(library.installed) {
        if (!library.installed) setupOpen = true
        else setupOpen = false
    }

    MaterialTheme {
        Scaffold(
            containerColor = Ink,
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                if (!setupOpen) {
                    NavigationBar(
                        containerColor = Surface,
                        contentColor = Color.White,
                        tonalElevation = 0.dp
                    ) {
                        Page.entries.forEach { item ->
                            NavigationBarItem(
                                selected = selected == item,
                                onClick = { page = item.name },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    indicatorColor = Violet.copy(alpha = .25f),
                                    unselectedIconColor = Lilac,
                                    unselectedTextColor = Lilac
                                )
                            )
                        }
                    }
                }
            }
        ) { insets ->
            AnimatedContent(
                targetState = setupOpen to selected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "main screen"
            ) { (isSetup, activePage) ->
                Box(Modifier.fillMaxSize().padding(insets)) {
                    if (isSetup) SetupScreen(library, viewModel, onBack = { setupOpen = false })
                    else when (activePage) {
                        Page.Home -> HomeScreen(library, onSetup = { setupOpen = true }, onSongs = { page = Page.Songs.name }, onFavorite = { viewModel.setFavorite(it); Unit }, onAdd = { song, singer -> viewModel.addToQueue(song, singer); Unit }, onPlay = { viewModel.playSong(it); page = Page.Playing.name }, onCheckUpdates = { viewModel.checkForUpdates() })
                        Page.Songs -> SongsScreen(library, query, onQuery = { query = it; viewModel.search(it) }, onSetup = { setupOpen = true }, onFavorite = { viewModel.setFavorite(it); Unit }, onAdd = { song, singer -> viewModel.addToQueue(song, singer); Unit }, onPlay = { viewModel.playSong(it); page = Page.Playing.name })
                        Page.Queue -> QueueScreen(library.queue, onSongs = { page = Page.Songs.name }, onRemove = { viewModel.removeFromQueue(it); Unit }, onMove = { id, direction -> viewModel.moveQueueItem(id, direction); Unit }, onClear = { viewModel.clearQueue(); Unit })
                        Page.Playing -> PlayingScreen(library, onSetup = { setupOpen = true }, onBrowseSongs = { page = Page.Songs.name }, onToggle = { viewModel.togglePlayback() }, onSeek = { viewModel.seekPlayback(it) })
                        Page.Settings -> SettingsScreen(
                            library = library,
                            onSetup = { setupOpen = true },
                            onPickBackgroundFromPhone = { backgroundPicker.launch(arrayOf("video/*")) },
                            onSelectDownloadedBackground = { viewModel.selectDownloadedBackground(it) },
                            onClearBackground = { viewModel.clearBackgroundVideo() },
                            onSelectSoundFont = { viewModel.selectSoundFont(it) },
                            onImportSoundFont = { soundFontPicker.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
        }
        if (library.updateCheckBusy || library.updateDownloadBusy || library.updateInfo != null || library.updateMessage != null) {
            UpdateDialog(
                library = library,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onDownload = { viewModel.downloadAndInstallUpdate() },
                onInstall = { viewModel.installDownloadedUpdate() },
                onLibraryUpdate = { viewModel.dismissUpdateDialog(); setupOpen = true }
            )
        }
    }
}

@Composable
private fun HomeScreen(library: LibraryUiState, onSetup: () -> Unit, onSongs: () -> Unit, onFavorite: (SongEntity) -> Unit, onAdd: (SongEntity, String) -> Unit, onPlay: (SongEntity) -> Unit, onCheckUpdates: () -> Unit) {
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Neon), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("JKTV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("OFFLINE KARAOKE", color = Lilac, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            IconButton(
                onClick = onCheckUpdates,
                enabled = !library.updateCheckBusy && !library.updateDownloadBusy,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Card).border(1.dp, Neon, CircleShape)
            ) { Icon(Icons.Rounded.Refresh, "Check for updates", tint = Violet) }
        }
        Spacer(Modifier.height(28.dp))
        Text("Your stage,\nready when you are.", color = Color.White, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        Text("Sing together. Works offline after setup.", color = Lilac, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        GradientCard {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot()
                    Spacer(Modifier.width(9.dp))
                    Text(if (library.installed) "OFFLINE LIBRARY READY" else "LIBRARY SETUP REQUIRED", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                Text(if (library.installed) "${library.songCount} songs are indexed on this device." else "Your song library and SoundFont will be stored on this device.", color = Lilac, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
                Button(
                    onClick = onSetup,
                    modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (library.installed) "Manage offline library" else "Set up offline library", fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("SONGS", if (library.installed) library.songCount.toString() else "—", Icons.Rounded.LibraryMusic, Modifier.weight(1f))
            StatCard("SOUND", if (library.installed) "Installed" else "Not installed", Icons.Rounded.Equalizer, Modifier.weight(1f))
        }
        SectionTitle("Your library", "Browse songs", onSongs)
        if (library.songs.isNotEmpty()) SongCard(library.songs.first(), onFavorite = { onFavorite(library.songs.first()) }, onAdd = { singer -> onAdd(library.songs.first(), singer) }, onPlay = { onPlay(library.songs.first()) })
        else EmptyCard("No songs installed yet", "Download the library once to start singing offline.", Icons.Rounded.Album)
    }
}

@Composable
private fun SongsScreen(library: LibraryUiState, query: String, onQuery: (String) -> Unit, onSetup: () -> Unit, onFavorite: (SongEntity) -> Unit, onAdd: (SongEntity, String) -> Unit, onPlay: (SongEntity) -> Unit) {
    ScreenColumn {
        Header("Songs", "OFFLINE LIBRARY")
        SearchField(query, onQuery)
        Spacer(Modifier.height(14.dp))
        if (library.songs.isEmpty()) {
            EmptyCard("Your song list will appear here", "Install the MIDI/KAR library to search by title, artist, or song number.", Icons.Rounded.Search)
            PrimaryAction("Install song library", onSetup)
        } else {
            Text("${library.songCount} songs · showing ${library.songs.size}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            library.songs.forEach { song -> SongCard(song, onFavorite = { onFavorite(song) }, onAdd = { singer -> onAdd(song, singer) }, onPlay = { onPlay(song) }) }
        }
    }
}

@Composable
private fun QueueScreen(queue: List<QueueRow>, onSongs: () -> Unit, onRemove: (Long) -> Unit, onMove: (Long, Int) -> Unit, onClear: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the singer queue?", color = Color.White) },
            text = { Text("All queued songs will be removed.", color = Lilac) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear queue", color = Orange) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Keep queue", color = Violet) } },
            containerColor = Card
        )
    }
    ScreenColumn {
        Header("Singer queue", "YOUR TURN, YOUR ORDER")
        GradientCard {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("NOW SINGING", color = Lilac, fontSize = 11.sp, letterSpacing = 1.sp); Text("No song playing", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp)) }
                Column(horizontalAlignment = Alignment.End) {
                    Text("UP NEXT", color = Lilac, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text(queue.firstOrNull()?.title ?: "Queue is empty", color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    queue.firstOrNull()?.let { Text(it.singer, color = Lilac, fontSize = 12.sp) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (queue.isEmpty()) {
            EmptyCard("Queue is ready", "Add a song from your library and assign a singer.", Icons.Rounded.QueueMusic)
            PrimaryAction("Browse songs", onSongs)
        } else {
            androidx.compose.material3.TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.End)) { Text("Clear queue", color = Lilac) }
            queue.forEachIndexed { index, item -> QueueCard(index + 1, item, canMoveUp = index > 0, canMoveDown = index < queue.lastIndex, onMoveUp = { onMove(item.id, -1) }, onMoveDown = { onMove(item.id, 1) }, onRemove = { onRemove(item.id) }) }
        }
    }
}

@Composable
private fun PlayingScreen(library: LibraryUiState, onSetup: () -> Unit, onBrowseSongs: () -> Unit, onToggle: () -> Unit, onSeek: (Long) -> Unit) {
    val playback = library.playback
    val duration = playback.durationMs.coerceAtLeast(0L)
    var isSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember(playback.file?.absolutePath, duration) {
        mutableStateOf(if (duration > 0L) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f)
    }
    LaunchedEffect(playback.positionMs, isSeeking) {
        if (!isSeeking) sliderValue = if (duration > 0L) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }
    val currentLyricIndex = playback.lyrics.indexOfLast { it.timeMs <= playback.positionMs }
    val displayedLyricIndex = currentLyricIndex.coerceAtLeast(0)
    val currentLyric = playback.lyrics.getOrNull(displayedLyricIndex)
    val nextLyric = playback.lyrics.getOrNull(displayedLyricIndex + 1)
    val backgroundUri = library.backgroundVideoUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
    var videoError by remember(backgroundUri?.toString()) { mutableStateOf<String?>(null) }
    ScreenColumn {
        Header("Now playing", "KARAOKE STAGE")
        // ScreenColumn is a LazyColumn with unbounded height for its item content. A weighted
        // child there can be measured at 0px, hiding both the video surface and the lyric stage.
        Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Card, Surface))), contentAlignment = Alignment.Center) {
            if (backgroundUri != null) {
                LoopingVideoBackground(backgroundUri, Modifier.fillMaxSize()) { message -> videoError = message }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (backgroundUri == null) 0.08f else 0.48f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(24.dp)) {
                if (playback.file == null) {
                    Text("♪", color = Violet, fontSize = 32.sp)
                    Text("Choose a song to begin", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text("Timed lyrics appear here when the song includes them", color = Lilac, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
                } else if (playback.lyrics.isEmpty()) {
                    Text("♪", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("No timed lyrics in this file", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                    Text("No timed lyrics are embedded in this song. Add a same-name .lrc file beside it, or use a lyric-enabled .KAR file.", color = Lilac, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
                } else {
                    Text(currentLyric?.text ?: "♪", color = Color.White, fontSize = 27.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (nextLyric != null) Text(nextLyric.text, color = Color.White.copy(alpha = .78f), fontSize = 17.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                }
                if (videoError != null) {
                    Text(videoError.orEmpty(), color = Orange, fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(library.currentSong?.title ?: "Nothing playing", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(library.currentSong?.artist?.ifBlank { "Unknown artist" } ?: "Local MIDI synthesizer · FluidSynth", color = Lilac, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it; isSeeking = true },
            onValueChangeFinished = {
                if (duration > 0L) onSeek((sliderValue * duration).toLong())
                isSeeking = false
            },
            enabled = playback.file != null && duration > 0L
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(playback.positionMs), color = Lilac, fontSize = 12.sp)
            Text(formatPlaybackTime(duration), color = Lilac, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBrowseSongs) { Icon(Icons.Rounded.LibraryMusic, "Browse songs", tint = Lilac) }
            IconButton(onClick = onToggle, enabled = playback.file != null, modifier = Modifier.size(72.dp).clip(CircleShape).background(Violet)) {
                Icon(if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playback.playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(38.dp))
            }
        }
        if (!library.error.isNullOrBlank() && playback.file == null) Text(library.error, color = Orange, fontSize = 13.sp, lineHeight = 19.sp)
        if (!library.installed) {
            Spacer(Modifier.height(8.dp))
            PrimaryAction("Set up offline library", onSetup)
        }
    }
}

@Composable
private fun SettingsScreen(
    library: LibraryUiState,
    onSetup: () -> Unit,
    onPickBackgroundFromPhone: () -> Unit,
    onSelectDownloadedBackground: (String) -> Unit,
    onClearBackground: () -> Unit,
    onSelectSoundFont: (String) -> Unit,
    onImportSoundFont: () -> Unit
) {
    var showSoundFonts by rememberSaveable { mutableStateOf(false) }
    var showBackgroundVideos by rememberSaveable { mutableStateOf(false) }
    val selectedFont = library.soundFonts.firstOrNull { it.path == library.selectedSoundFontPath }

    ScreenColumn {
        Header("Settings", "MAKE IT YOURS")
        SettingRow("Offline library", "Download or check for song updates", Icons.Rounded.LibraryMusic, onSetup)
        Text("KARAOKE DISPLAY", color = Lilac, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        SettingRow("Background video", library.backgroundVideoName ?: "No video selected", Icons.Rounded.VideoLibrary, { showBackgroundVideos = true })
        if (library.backgroundVideoUri != null) {
            TextButton(onClick = onClearBackground, modifier = Modifier.align(Alignment.End)) { Text("Clear background", color = Lilac) }
        }
        Text("Choose a video saved on your phone. It loops silently behind the lyrics.", color = Lilac, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
        Text("MIDI AUDIO", color = Lilac, fontSize = 11.sp, letterSpacing = 1.1.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        SettingRow("SoundFont", selectedFont?.name ?: "Set up the library or import an SF2 file", Icons.Rounded.Equalizer, { showSoundFonts = true })
        if (!library.settingsMessage.isNullOrBlank()) Text(library.settingsMessage, color = if (library.settingsMessage.contains("could not", true) || library.settingsMessage.contains("valid", true)) Orange else Lilac, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp))
        EmptyCard("MIDI playback", "FluidSynth uses the selected SoundFont to play MIDI and KAR songs offline on this device.", Icons.Rounded.Equalizer)
        Text("About JKTV Offline Karaoke · ${BuildConfig.VERSION_NAME}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
    }

    if (showBackgroundVideos) {
        AlertDialog(
            onDismissRequest = { showBackgroundVideos = false },
            title = { Text("Choose a background video", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max = 380.dp)) {
                    Text("Videos included in your downloaded library work offline.", color = Lilac, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 8.dp))
                    if (library.backgroundVideos.isEmpty()) {
                        Text("No R2 background videos are installed yet. Download the updated library, or choose a video from this phone.", color = Lilac, fontSize = 14.sp, lineHeight = 21.sp)
                    } else {
                        LazyColumn(Modifier.weight(1f, fill = false)) {
                            items(library.backgroundVideos) { video ->
                                val selected = library.backgroundVideoUri?.let { Uri.parse(it).path == video.path } == true
                                androidx.compose.material3.Surface(
                                    onClick = { showBackgroundVideos = false; onSelectDownloadedBackground(video.path) },
                                    color = Card,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selected, onClick = { showBackgroundVideos = false; onSelectDownloadedBackground(video.path) })
                                        Text(video.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundVideos = false; onPickBackgroundFromPhone() }) { Text("Choose from phone", color = Violet) }
            },
            dismissButton = { TextButton(onClick = { showBackgroundVideos = false }) { Text("Done", color = Lilac) } },
            containerColor = Card
        )
    }

    if (showSoundFonts) {
        AlertDialog(
            onDismissRequest = { showSoundFonts = false },
            title = { Text("Choose a SoundFont", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max = 360.dp)) {
                    Text("Select an installed SF2 bank or import one from this phone.", color = Lilac, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 8.dp))
                    if (library.soundFonts.isEmpty()) {
                        Text("No SoundFonts are installed yet. Finish offline library setup or import an .sf2 file.", color = Lilac, fontSize = 14.sp, lineHeight = 21.sp)
                    } else {
                        LazyColumn {
                            items(library.soundFonts.size) { index ->
                                val font = library.soundFonts[index]
                                androidx.compose.material3.Surface(
                                    onClick = { showSoundFonts = false; onSelectSoundFont(font.path) },
                                    color = Card,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = font.path == library.selectedSoundFontPath, onClick = { showSoundFonts = false; onSelectSoundFont(font.path) })
                                        Text(font.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundFonts = false; onImportSoundFont() }) { Text("Import SF2", color = Violet) }
            },
            dismissButton = { TextButton(onClick = { showSoundFonts = false }) { Text("Done", color = Lilac) } },
            containerColor = Card
        )
    }
}

@Composable
private fun LoopingVideoBackground(uri: Uri, modifier: Modifier = Modifier, onError: (String) -> Unit) {
    key(uri.toString()) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextureView(context).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var player: MediaPlayer? = null
                        private var outputSurface: Surface? = null

                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(surfaceTexture)
                            val mediaPlayer = MediaPlayer()
                            outputSurface = surface
                            player = mediaPlayer
                            try {
                                mediaPlayer.setSurface(surface)
                                // Library BGVs are app-private files; pass their path directly to avoid
                                // ContentResolver URI handling differences across Android vendors.
                                if (uri.scheme == "file") {
                                    mediaPlayer.setDataSource(uri.path ?: throw IllegalArgumentException("The selected video path is empty."))
                                } else {
                                    mediaPlayer.setDataSource(context, uri)
                                }
                                mediaPlayer.isLooping = true
                                mediaPlayer.setVolume(0f, 0f)
                                mediaPlayer.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                                    applyVideoCenterCrop(this@apply, videoWidth, videoHeight)
                                }
                                mediaPlayer.setOnPreparedListener { prepared ->
                                    applyVideoCenterCrop(this@apply, prepared.videoWidth, prepared.videoHeight)
                                    prepared.start()
                                }
                                mediaPlayer.setOnErrorListener { failedPlayer, what, extra ->
                                    onError(if (what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) "This MP4 codec is not supported. Choose an H.264 MP4 video." else "Background video playback failed ($what/$extra). Try another MP4.")
                                    runCatching { failedPlayer.release() }
                                    if (player === failedPlayer) player = null
                                    true
                                }
                                mediaPlayer.prepareAsync()
                            } catch (error: Exception) {
                                onError(error.localizedMessage ?: "Couldn't open the selected background video.")
                                runCatching { mediaPlayer.release() }
                                if (player === mediaPlayer) player = null
                                runCatching { surface.release() }
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            player?.let { applyVideoCenterCrop(this@apply, it.videoWidth, it.videoHeight) }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            player?.let { active -> runCatching { active.stop() }; runCatching { active.release() } }
                            player = null
                            outputSurface?.let { runCatching { it.release() } }
                            outputSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                    }
                }
            }
        )
    }
}

private fun applyVideoCenterCrop(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val scale = maxOf(view.width.toFloat() / videoWidth, view.height.toFloat() / videoHeight)
    val x = (view.width - videoWidth * scale) / 2f
    val y = (view.height - videoHeight * scale) / 2f
    view.setTransform(Matrix().apply { setScale(scale, scale); postTranslate(x, y) })
}

@Composable
private fun UpdateDialog(
    library: LibraryUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onLibraryUpdate: () -> Unit
) {
    val update = library.updateInfo
    val libraryUpdate = library.libraryUpdateManifest
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    library.updateCheckBusy -> "Checking for updates"
                    update != null || libraryUpdate != null -> "New JKTV Update Available!"
                    library.updateMessage == "You're Up to Date!" -> "You're Up to Date!"
                    else -> "GitHub Update Check"
                },
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    when {
                        library.updateCheckBusy -> "Checking GitHub for signed APK releases…"
                        update != null -> "APK update: version ${update.versionName} is available. Download and verify it, then Android will ask you to approve installation. Your offline files and app data are preserved by an in-place update."
                        libraryUpdate != null -> "Offline library version ${libraryUpdate.libraryVersion} is available. Review the component details, then open setup to check storage and install it."
                        else -> library.updateMessage.orEmpty()
                    },
                    color = Lilac,
                    lineHeight = 20.sp
                )
                if (update != null && libraryUpdate != null) {
                    Text("Song packages: ${libraryUpdate.packages.count { it.fileName.endsWith(".zip", true) }} · SoundFonts: ${libraryUpdate.packages.count { it.fileName.endsWith(".sf2", true) }} · Background videos: ${libraryUpdate.packages.count { it.fileName.endsWith(".mp4", true) }} · new download ${formatBytes(library.libraryUpdateDownloadBytes ?: 0L)}", color = Lilac, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                if (update == null && libraryUpdate != null) {
                    Text("Song packages: ${libraryUpdate.packages.count { it.fileName.endsWith(".zip", true) }} · SoundFonts: ${libraryUpdate.packages.count { it.fileName.endsWith(".sf2", true) }} · Background videos: ${libraryUpdate.packages.count { it.fileName.endsWith(".mp4", true) }} · new download ${formatBytes(library.libraryUpdateDownloadBytes ?: 0L)}", color = Lilac, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                if (library.updateCheckBusy || library.updateDownloadBusy) {
                    LinearProgressIndicator(
                        progress = { if (library.updateCheckBusy) 0f else library.updateProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        color = Violet,
                        trackColor = Card
                    )
                }
                if (library.updateDownloadBusy) {
                    Text("${(library.updateProgress * 100).toInt()}% downloaded", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                if (library.updateReadyPath != null && !library.updateDownloadBusy) {
                    Text(library.updateMessage.orEmpty(), color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                }
            }
        },
        confirmButton = {
            Row {
                if (libraryUpdate != null && !library.updateCheckBusy && !library.updateDownloadBusy) {
                    androidx.compose.material3.TextButton(onClick = onLibraryUpdate) { Text("Update Library", color = Lilac) }
                }
                if (update != null && library.updateReadyPath != null && !library.updateDownloadBusy) {
                    androidx.compose.material3.TextButton(onClick = onInstall) { Text("Install Update", color = Violet) }
                } else if (update != null && !library.updateDownloadBusy && !library.updateCheckBusy) {
                    androidx.compose.material3.TextButton(onClick = onDownload) { Text("Update Now", color = Violet) }
                }
                if (update == null && libraryUpdate == null && !library.updateCheckBusy && !library.updateDownloadBusy) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close", color = Violet) }
                }
            }
        },
        dismissButton = {
            if ((update != null || libraryUpdate != null) && !library.updateDownloadBusy) androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Later", color = Lilac) }
        },
        containerColor = Card
    )
}

@Composable
private fun SetupScreen(library: LibraryUiState, viewModel: KaraokeViewModel, onBack: () -> Unit) {
    val manifestUrl = BuildConfig.LIBRARY_MANIFEST_URL.trim()
    val manifestReady = library.checkedManifestUrl == manifestUrl && library.requiredBytes != null
    val hasEnoughStorage = library.availableBytes != null && library.requiredBytes != null && library.availableBytes >= library.requiredBytes
    val canDownload = manifestReady && hasEnoughStorage && !library.busy && manifestUrl.isNotBlank()

    LaunchedEffect(manifestUrl) {
        if (manifestUrl.isNotBlank() && library.checkedManifestUrl != manifestUrl && !library.busy) {
            viewModel.inspectManifest(manifestUrl)
        }
    }

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Text("‹", color = Color.White, fontSize = 32.sp) }
            Column(Modifier.padding(start = 8.dp)) {
                Text("Get your library", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("OFFLINE SETUP", color = Lilac, fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(Neon),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(54.dp))
            }
            Text("JKTV KARAOKE", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 16.dp))
            Text("OFFLINE LIBRARY", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Download your songs and sounds once, then sing anytime — even offline.",
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 17.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(Modifier.height(22.dp))
        GradientCard {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (library.installed) "100%" else "${(library.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = Violet,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    if (library.busy) {
                        Text("IN PROGRESS", color = Lilac, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                LinearProgressIndicator(
                    progress = { if (library.installed) 1f else library.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 8.dp).clip(CircleShape),
                    color = Violet,
                    trackColor = Card
                )
                val status = when {
                    library.busy -> library.progressText ?: "Preparing your library…"
                    library.paused -> "Paused. Your downloaded files are saved."
                    library.installed -> "Your offline library is ready."
                    else -> "Not downloaded yet."
                }
                Text(status, color = Lilac, fontSize = 14.sp, modifier = Modifier.padding(top = 13.dp))
                Text(
                    when {
                        library.downloadBytes != null -> "Songs and SoundFonts · ${formatBytes(library.downloadBytes)}"
                        library.busy -> "Checking the resource size…"
                        else -> "Songs and SoundFonts · size not available"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (library.busy && library.downloadedBytes > 0L) {
                    val speedText = if (library.downloadSpeedBytesPerSecond > 0L) " · ${formatBytes(library.downloadSpeedBytesPerSecond)}/s" else ""
                    val remainingText = library.remainingSeconds?.let { " · ${formatDuration(it)} left" }.orEmpty()
                    Text(
                        "${formatBytes(library.downloadedBytes)} of ${formatBytes(library.downloadBytes ?: 0L)}$speedText$remainingText",
                        color = Lilac,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (library.busy && (library.downloadedBytes > 0L || (manifestReady && library.progressText?.contains("asset manifest", ignoreCase = true) != true))) {
            Spacer(Modifier.height(15.dp))
            PrimaryAction("Pause download") { viewModel.pauseSetup() }
        }

        library.requiredBytes?.let { required ->
            val available = library.availableBytes
            Text(
                if (available == null) "Space needed for download and safe install: ${formatBytes(required)}"
                else "Needs ${formatBytes(required)} of phone storage · ${formatBytes(available)} available",
                color = if (available != null && available < required) Orange else Lilac,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 11.dp, start = 4.dp, end = 4.dp)
            )
        }

        if (manifestUrl.isBlank()) {
            Text("This app build has no library manifest configured.", color = Orange, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp))
        }
        library.error?.let { Text(it, color = Orange, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp)) }

        if (!library.busy) {
            val actionLabel = when {
                manifestUrl.isBlank() -> "DOWNLOAD UNAVAILABLE"
                !manifestReady && library.error != null -> "RETRY RESOURCE CHECK"
                !manifestReady -> "CHECKING RESOURCES…"
                !hasEnoughStorage -> "NOT ENOUGH PHONE STORAGE"
                library.paused -> "RESUME DOWNLOAD"
                library.installed -> "UPDATE OFFLINE LIBRARY"
                else -> "DOWNLOAD ALL RESOURCES"
            }
            Button(
                onClick = { if (manifestReady) viewModel.setup(manifestUrl) else viewModel.inspectManifest(manifestUrl) },
                enabled = canDownload || (!library.busy && manifestUrl.isNotBlank() && !manifestReady && library.error != null),
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, disabledContainerColor = Card),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(actionLabel, color = if (canDownload || (!library.busy && !manifestReady && library.error != null)) Color.White else Lilac, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        Text(
            if (library.installed) "Your library stays on this phone for offline use."
            else "Best on Wi-Fi. You can pause and resume later; completed downloads are kept.",
            color = Lilac,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 13.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        GradientCard {
            Column(Modifier.padding(18.dp)) {
                Text("READY FOR OFFLINE SINGING", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                SetupItem("MIDI/KAR song packs", if (library.installed) "Ready" else "Included")
                SetupItem("SoundFont instruments", if (library.installed) "Ready" else "Included")
                SetupItem("Song index", if (library.installed) "On this phone" else "Built after download")
            }
        }
        Text("MIDI playback uses the installed SF2 SoundFont and runs offline on this device.", color = Lilac, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val minutes = safe / 60
    val remainingSeconds = safe % 60
    return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
}

@Composable
private fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = { item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 680.dp), content = content)
            }
        } }
    )
}

@Composable
private fun Header(title: String, eyebrow: String) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(eyebrow, color = Lilac, fontSize = 11.sp, letterSpacing = 1.7.sp)
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun GradientCard(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Neon).padding(1.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Surface)) { content() }
    }
}

@Composable
private fun StatusDot() {
    Box(Modifier.size(10.dp).clip(CircleShape).background(Orange).border(2.dp, Violet, CircleShape))
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Card).padding(16.dp)) {
        Icon(icon, null, tint = Violet, modifier = Modifier.size(21.dp))
        Text(title, color = Lilac, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 14.dp))
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        androidx.compose.material3.TextButton(onClick = onClick) { Text(action, color = Violet) }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).padding(20.dp)) {
        Icon(icon, null, tint = Violet, modifier = Modifier.size(25.dp))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Text(body, color = Lilac, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(17.dp)).background(Card).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, "Search songs", tint = Lilac, modifier = Modifier.size(21.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            decorationBox = { inner -> if (value.isEmpty()) Text("Search title, artist, or number", color = Lilac, fontSize = 14.sp); inner() }
        )
    }
}

@Composable
private fun SongCard(song: SongEntity, onFavorite: () -> Unit, onAdd: (String) -> Unit, onPlay: () -> Unit) {
    var assignSinger by rememberSaveable { mutableStateOf(false) }
    var singer by rememberSaveable { mutableStateOf("Guest") }
    if (assignSinger) {
        AlertDialog(
            onDismissRequest = { assignSinger = false },
            title = { Text("Add song to queue", color = Color.White) },
            text = {
                Column {
                    Text(song.title, color = Lilac, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = singer,
                        onValueChange = { singer = it.take(80) },
                        label = { Text("Singer name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Violet, unfocusedBorderColor = Lilac, focusedLabelColor = Violet, unfocusedLabelColor = Lilac)
                    )
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onAdd(singer.ifBlank { "Guest" }); assignSinger = false }) { Text("Add", color = Violet) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { assignSinger = false }) { Text("Cancel", color = Lilac) } },
            containerColor = Card
        )
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Card).padding(start = 15.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Surface), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Violet, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist.ifBlank { "Unknown artist" }, color = Lilac, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.PlayArrow, "Play song", tint = Violet) }
        IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) { Icon(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = if (song.favorite) Orange else Lilac) }
        IconButton(onClick = { assignSinger = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Add to singer queue", tint = Violet) }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun QueueCard(number: Int, item: QueueRow, canMoveUp: Boolean, canMoveDown: Boolean, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Card).padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number.toString().padStart(2, '0'), color = Violet, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.singer} · ${item.artist}", color = Lilac, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowUpward, "Move song up", tint = if (canMoveUp) Lilac else Lilac.copy(alpha = .35f)) }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ArrowDownward, "Move song down", tint = if (canMoveDown) Lilac else Lilac.copy(alpha = .35f)) }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.DeleteOutline, "Remove from queue", tint = Lilac) }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet), shape = RoundedCornerShape(18.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, color = Card, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.height(72.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Violet, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Lilac, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = Lilac, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SetupItem(name: String, status: String) {
    Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(status, color = Orange, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
