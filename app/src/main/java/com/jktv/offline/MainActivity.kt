package com.jktv.offline

import android.os.Bundle
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val library = viewModel.state
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
                        Page.Home -> HomeScreen(library, onSetup = { setupOpen = true }, onSongs = { page = Page.Songs.name }, onFavorite = { viewModel.setFavorite(it); Unit }, onAdd = { song, singer -> viewModel.addToQueue(song, singer); Unit }, onCheckUpdates = { viewModel.checkForUpdates() })
                        Page.Songs -> SongsScreen(library, query, onQuery = { query = it; viewModel.search(it) }, onSetup = { setupOpen = true }, onFavorite = { viewModel.setFavorite(it); Unit }, onAdd = { song, singer -> viewModel.addToQueue(song, singer); Unit })
                        Page.Queue -> QueueScreen(library.queue, onSongs = { page = Page.Songs.name }, onRemove = { viewModel.removeFromQueue(it); Unit }, onMove = { id, direction -> viewModel.moveQueueItem(id, direction); Unit }, onClear = { viewModel.clearQueue(); Unit })
                        Page.Playing -> PlayingScreen(library, onSetup = { setupOpen = true })
                        Page.Settings -> SettingsScreen(onSetup = { setupOpen = true })
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
private fun HomeScreen(library: LibraryUiState, onSetup: () -> Unit, onSongs: () -> Unit, onFavorite: (SongEntity) -> Unit, onAdd: (SongEntity, String) -> Unit, onCheckUpdates: () -> Unit) {
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
        if (library.songs.isNotEmpty()) SongCard(library.songs.first(), onFavorite = { onFavorite(library.songs.first()) }, onAdd = { singer -> onAdd(library.songs.first(), singer) })
        else EmptyCard("No songs installed yet", "Download the library once to start singing offline.", Icons.Rounded.Album)
    }
}

@Composable
private fun SongsScreen(library: LibraryUiState, query: String, onQuery: (String) -> Unit, onSetup: () -> Unit, onFavorite: (SongEntity) -> Unit, onAdd: (SongEntity, String) -> Unit) {
    ScreenColumn {
        Header("Songs", "OFFLINE LIBRARY")
        SearchField(query, onQuery)
        Spacer(Modifier.height(14.dp))
        if (library.songs.isEmpty()) {
            EmptyCard("Your song list will appear here", "Install the MIDI/KAR library to search by title, artist, or song number.", Icons.Rounded.Search)
            PrimaryAction("Install song library", onSetup)
        } else {
            Text("${library.songCount} songs · showing ${library.songs.size}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            library.songs.forEach { song -> SongCard(song, onFavorite = { onFavorite(song) }, onAdd = { singer -> onAdd(song, singer) }) }
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
private fun PlayingScreen(library: LibraryUiState, onSetup: () -> Unit) {
    ScreenColumn {
        Header("Now playing", "KARAOKE STAGE")
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).height(250.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Card, Surface))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("♪", color = Violet, fontSize = 32.sp)
                Text("Your lyrics will shine here", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Text("Choose a song to begin", color = Lilac, fontSize = 14.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (library.installed) "PLAYBACK ENGINE UNAVAILABLE" else "NO SONG LIBRARY", color = Lilac, fontSize = 11.sp, letterSpacing = 1.3.sp)
        Text(if (library.installed) "The native MIDI engine must be integrated before songs can play." else "Install your offline library to prepare this stage.", color = Color.White, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 7.dp))
        if (!library.installed) {
            Spacer(Modifier.height(16.dp))
            PrimaryAction("Set up offline library", onSetup)
        }
    }
}

@Composable
private fun SettingsScreen(onSetup: () -> Unit) {
    ScreenColumn {
        Header("Settings", "MAKE IT YOURS")
        SettingRow("Offline library", "Download or check for song updates", Icons.Rounded.LibraryMusic, onSetup)
        EmptyCard("Playback engine pending", "SoundFont selection, key and tempo controls, and audio effects will be available after the native synthesizer is integrated.", Icons.Rounded.Equalizer)
        Text("About JKTV Offline Karaoke · ${BuildConfig.VERSION_NAME}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
    }
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
                    Text("Song packages: ${libraryUpdate.packages.count { it.fileName.endsWith(".zip", true) }} · SoundFonts: ${libraryUpdate.packages.count { it.fileName.endsWith(".sf2", true) }} · new download ${formatBytes(library.libraryUpdateDownloadBytes ?: 0L)}", color = Lilac, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                }
                if (update == null && libraryUpdate != null) {
                    Text("Song packages: ${libraryUpdate.packages.count { it.fileName.endsWith(".zip", true) }} · SoundFonts: ${libraryUpdate.packages.count { it.fileName.endsWith(".sf2", true) }} · new download ${formatBytes(library.libraryUpdateDownloadBytes ?: 0L)}", color = Lilac, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
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
    var manifestUrl by rememberSaveable { mutableStateOf(BuildConfig.GITHUB_LIBRARY_MANIFEST_URL) }
    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Text("‹", color = Color.White, fontSize = 32.sp) }
            Column(Modifier.padding(start = 8.dp)) {
                Text("Prepare your library", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("OFFLINE SETUP", color = Lilac, fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        GradientCard {
            Column(Modifier.padding(20.dp)) {
                Text("JKTV KARAOKE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text("Preparing your offline karaoke experience", color = Lilac, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(20.dp))
                SetupItem("MIDI/KAR song library", if (library.installed) "Installed" else "Needed")
                SetupItem("SoundFont instrument bank", if (library.installed) "Installed" else "Needed")
                SetupItem("Song metadata index", "Created on this device")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("MANIFEST HTTPS ADDRESS", color = Lilac, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        ManifestUrlField(manifestUrl, onValue = { manifestUrl = it })
        Text("Enter the complete HTTPS address of your GitHub library-manifest.json file.", color = Lilac, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
        if (library.checkedManifestUrl == manifestUrl.trim() && library.requiredBytes != null) {
            Text("${library.packageCount} packages · ${formatBytes(library.downloadBytes ?: 0L)} download", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 15.dp))
            Text("Space needed for download and safe install: ${formatBytes(library.requiredBytes)}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            library.availableBytes?.let { Text("Free storage: ${formatBytes(it)}", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) }
            if (library.availableBytes != null && library.availableBytes < library.requiredBytes) {
                Text("Free space is below the download size.", color = Orange, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (library.busy) {
            LinearProgressIndicator(progress = { library.progress }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), color = Violet, trackColor = Card)
            Text(library.progressText.orEmpty(), color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            val speedText = if (library.downloadSpeedBytesPerSecond > 0L) "${formatBytes(library.downloadSpeedBytesPerSecond)}/s" else "Starting…"
            val remainingText = library.remainingSeconds?.let { " · ${formatDuration(it)} left" }.orEmpty()
            Text("${formatBytes(library.downloadedBytes)} / ${formatBytes(library.downloadBytes ?: 0L)} · $speedText$remainingText", color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            PrimaryAction("Pause download") { viewModel.pauseSetup() }
        }
        if (library.paused) {
            Text(library.progressText.orEmpty(), color = Lilac, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        library.error?.let { Text(it, color = Orange, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp)) }
        if (!library.busy) {
            if (library.checkedManifestUrl == manifestUrl.trim() && library.requiredBytes != null && library.availableBytes != null && library.availableBytes >= library.requiredBytes) {
                val action = when {
                    library.paused -> "Resume download"
                    library.installed -> "Download library update"
                    else -> "Download offline library"
                }
                PrimaryAction(action) { viewModel.setup(manifestUrl) }
            } else if (library.checkedManifestUrl != manifestUrl.trim() || library.requiredBytes == null) {
                PrimaryAction("Check required download size") { viewModel.inspectManifest(manifestUrl) }
            }
        }
        Text("The native MIDI synthesizer still needs its Android engine build before playback is available.", color = Lilac, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp))
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
private fun ManifestUrlField(value: String, onValue: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(17.dp)).background(Card).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("https://github.com/hillman3948-cyber/JKTV-Offline/releases/latest/download/library-manifest.json", color = Lilac, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        )
    }
}

@Composable
private fun SongCard(song: SongEntity, onFavorite: () -> Unit, onAdd: (String) -> Unit) {
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
        IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) { Icon(if (song.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Toggle favorite", tint = if (song.favorite) Orange else Lilac) }
        IconButton(onClick = { assignSinger = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Add to singer queue", tint = Violet) }
    }
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
