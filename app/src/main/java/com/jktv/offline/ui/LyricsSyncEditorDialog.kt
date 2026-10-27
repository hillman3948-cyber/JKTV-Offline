package com.jktv.offline.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jktv.offline.playback.LatencyCalibration
import com.jktv.offline.playback.LyricLine
import com.jktv.offline.playback.LyricSyllable
import com.jktv.offline.playback.LyricsSyncEngine
import com.jktv.offline.playback.MelodyNote
import com.jktv.offline.playback.PlaybackState
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class SyncEditorTab(val label: String) {
    TAP_SYNC("1. Tap-to-Sync"),
    FINE_TUNE("2. Fine-Tune Timing"),
    TEXT_MELODY("3. Lyrics Text & Auto-Align")
}

data class EditableSyllableItem(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val beginsLine: Boolean,
    val endsWord: Boolean = true
)

/**
 * Full-screen Built-In Karaoke Lyrics Synchronization Studio & Editor.
 * Supports real-time MIDI playback, Tap-to-Sync syllable stamping, fine timestamp nudging,
 * automatic MelodyNote alignment, LRC/JSON import & export, local save, and restore original.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsSyncEditorDialog(
    songTitle: String,
    playback: PlaybackState,
    palette: LyricColorPalette,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPreviewLinesChanged: (List<LyricLine>) -> Unit,
    onSaveSync: (List<LyricLine>) -> Unit,
    onRestoreOriginal: () -> Unit,
    onImportTimingFile: () -> Unit,
    onExportLrcFile: (List<LyricLine>) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(SyncEditorTab.TAP_SYNC) }

    // Frame-accurate live playback position for 60fps preview & tap capture
    var liveSyncedPosMs by remember { mutableLongStateOf(playback.syncedLyricPositionMs()) }
    LaunchedEffect(playback.playing, playback.positionMs, playback.clockAnchorWallNs, playback.effectiveLatencyOffsetMs) {
        liveSyncedPosMs = playback.syncedLyricPositionMs()
        if (playback.playing) {
            while (true) {
                withFrameNanos { frameNs ->
                    liveSyncedPosMs = playback.syncedLyricPositionMs(frameNs)
                }
            }
        }
    }

    // Initialize editable syllables from current playback lyricLines
    var editableItems by remember(playback.file) {
        mutableStateOf(flattenLinesToEditable(playback.lyricLines))
    }
    var tapCursorIndex by remember(playback.file) { mutableIntStateOf(0) }
    var rawTextDraft by remember(playback.file) {
        mutableStateOf(
            if (playback.lyricLines.isNotEmpty()) {
                playback.lyricLines.joinToString("\n") { line ->
                    line.text
                }
            } else ""
        )
    }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val previewLines = remember(editableItems, playback.durationMs, playback.melodyNotes) {
        rebuildLinesFromEditable(editableItems, playback.melodyNotes, playback.durationMs)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF070B18),
            border = BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF00F0FF))
                            Text(
                                text = "LYRICS SYNCHRONIZATION STUDIO",
                                color = Color(0xFF00F0FF),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF152347)
                            ) {
                                Text(
                                    text = playback.syncSourceType.badgeText,
                                    color = Color(0xFFFFD600),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "$songTitle • ${editableItems.size} Syllables • ${playback.melodyNotes.size} Vocal Melody Notes",
                            color = Color(0xFFAABCE0),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Live Real-Time Karaoke Wipe Preview + Melody Timeline Bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0D142B),
                    border = BorderStroke(1.dp, palette.activeFill.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeLine = previewLines.firstOrNull {
                            liveSyncedPosMs in (it.startMs - 300L)..(it.endMs + 300L)
                        } ?: previewLines.lastOrNull { it.startMs <= liveSyncedPosMs }
                            ?: previewLines.firstOrNull()

                        Text(
                            text = "LIVE SYNC PREVIEW (${formatMsPrecise(liveSyncedPosMs)} / ${formatMsPrecise(playback.durationMs)})",
                            color = Color(0xFF00F0FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (activeLine != null) {
                            SyllableWipeLine(
                                line = activeLine,
                                positionMs = liveSyncedPosMs,
                                palette = palette,
                                fontSizeSp = 21f
                            )
                        } else {
                            Text(
                                text = "No syllables yet — enter lyrics in Tab 3 or import an .LRC file.",
                                color = Color(0xFF9FB2D9),
                                fontSize = 13.sp
                            )
                        }

                        // Visual Melody & Syllable Timeline Map
                        MelodyAndSyllableTimelineBar(
                            durationMs = playback.durationMs,
                            currentPosMs = liveSyncedPosMs,
                            melodyNotes = playback.melodyNotes,
                            syllables = editableItems,
                            primaryColor = palette.activeFill
                        )

                        // Playback Controls Row inside Editor
                        val duration = playback.durationMs.coerceAtLeast(1L)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onTogglePlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00F0FF),
                                    contentColor = Color(0xFF040914)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    if (playback.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (playback.playing) "Pause" else "Play", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { onSeek((liveSyncedPosMs - 3000L).coerceAtLeast(0L)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("-3s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = (liveSyncedPosMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                onValueChange = { frac -> onSeek((frac * duration).toLong()) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00F0FF),
                                    activeTrackColor = Color(0xFF00F0FF)
                                )
                            )
                            FilledTonalButton(
                                onClick = { onSeek((liveSyncedPosMs + 3000L).coerceAtMost(duration)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("+3s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncEditorTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = { Text(tab.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00F0FF),
                                selectedLabelColor = Color(0xFF040914)
                            )
                        )
                    }
                }

                statusBanner?.let { msg ->
                    Text(msg, color = Color(0xFF00FF9D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Tab Content Area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        SyncEditorTab.TAP_SYNC -> TapToSyncPanel(
                            items = editableItems,
                            cursorIndex = tapCursorIndex,
                            livePosMs = liveSyncedPosMs,
                            onSelectCursor = { tapCursorIndex = it.coerceIn(0, (editableItems.size - 1).coerceAtLeast(0)) },
                            onTapStampSyllable = {
                                if (editableItems.isNotEmpty()) {
                                    val idx = tapCursorIndex.coerceIn(0, editableItems.lastIndex)
                                    val stampedMs = liveSyncedPosMs.coerceAtLeast(0L)
                                    val updated = editableItems.toMutableList()
                                    if (idx > 0) {
                                        val prev = updated[idx - 1]
                                        if (stampedMs > prev.startMs) {
                                            updated[idx - 1] = prev.copy(endMs = min(prev.endMs, stampedMs))
                                        }
                                    }
                                    val cur = updated[idx]
                                    updated[idx] = cur.copy(
                                        startMs = stampedMs,
                                        endMs = max(stampedMs + 350L, cur.endMs)
                                    )
                                    editableItems = updated
                                    val rebuilt = rebuildLinesFromEditable(updated, playback.melodyNotes, playback.durationMs)
                                    onPreviewLinesChanged(rebuilt)
                                    if (idx < updated.lastIndex) {
                                        tapCursorIndex = idx + 1
                                    }
                                }
                            },
                            onToggleLineBreakAtCursor = {
                                if (editableItems.isNotEmpty()) {
                                    val idx = tapCursorIndex.coerceIn(0, editableItems.lastIndex)
                                    val updated = editableItems.toMutableList()
                                    updated[idx] = updated[idx].copy(beginsLine = !updated[idx].beginsLine)
                                    editableItems = updated
                                    onPreviewLinesChanged(rebuildLinesFromEditable(updated, playback.melodyNotes, playback.durationMs))
                                }
                            }
                        )

                        SyncEditorTab.FINE_TUNE -> FineTuneSyllablesPanel(
                            items = editableItems,
                            onSeekTo = onSeek,
                            onNudgeItem = { index, deltaMs ->
                                val updated = editableItems.toMutableList()
                                val item = updated[index]
                                val nextStart = (item.startMs + deltaMs).coerceAtLeast(0L)
                                val nextEnd = (item.endMs + deltaMs).coerceAtLeast(nextStart + 60L)
                                updated[index] = item.copy(startMs = nextStart, endMs = nextEnd)
                                editableItems = updated
                                onPreviewLinesChanged(rebuildLinesFromEditable(updated, playback.melodyNotes, playback.durationMs))
                            },
                            onShiftAll = { deltaMs ->
                                val updated = editableItems.map { item ->
                                    val s = (item.startMs + deltaMs).coerceAtLeast(0L)
                                    val e = (item.endMs + deltaMs).coerceAtLeast(s + 60L)
                                    item.copy(startMs = s, endMs = e)
                                }
                                editableItems = updated
                                onPreviewLinesChanged(rebuildLinesFromEditable(updated, playback.melodyNotes, playback.durationMs))
                                statusBanner = "Shifted all ${updated.size} syllables by ${if (deltaMs > 0) "+$deltaMs" else "$deltaMs"} ms."
                            }
                        )

                        SyncEditorTab.TEXT_MELODY -> TextAndMelodyAlignPanel(
                            rawText = rawTextDraft,
                            melodyNoteCount = playback.melodyNotes.size,
                            onRawTextChange = { rawTextDraft = it },
                            onLoadTokensForTapSync = {
                                val parsed = parseRawTextToEditable(rawTextDraft, playback.durationMs)
                                if (parsed.isNotEmpty()) {
                                    editableItems = parsed
                                    tapCursorIndex = 0
                                    onPreviewLinesChanged(rebuildLinesFromEditable(parsed, playback.melodyNotes, playback.durationMs))
                                    activeTab = SyncEditorTab.TAP_SYNC
                                    statusBanner = "Loaded ${parsed.size} syllables. Press Play and tap the sync button on each syllable!"
                                }
                            },
                            onAutoAlignToMelody = {
                                val tokens = rawTextDraft.lines()
                                    .flatMap { line -> line.trim().split(Regex("\\s+")) }
                                    .filter { it.isNotBlank() }
                                if (tokens.isNotEmpty() && playback.melodyNotes.isNotEmpty()) {
                                    val alignedLines = LyricsSyncEngine.autoAlignTokensToMelodyNotes(
                                        tokens = tokens,
                                        melodyNotes = playback.melodyNotes,
                                        durationMs = playback.durationMs
                                    )
                                    editableItems = flattenLinesToEditable(alignedLines)
                                    onPreviewLinesChanged(alignedLines)
                                    statusBanner = "Auto-aligned ${editableItems.size} syllables to ${playback.melodyNotes.size} MIDI vocal melody notes!"
                                } else {
                                    statusBanner = "Enter lyrics text above first (and ensure the MIDI has vocal melody notes)."
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // Footer Actions: Import, Export, Restore Original, Save Locally
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onImportTimingFile,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import .LRC/.JSON", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onExportLrcFile(previewLines) },
                            enabled = previewLines.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export .LRC", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onRestoreOriginal()
                                statusBanner = "Restored original embedded MIDI/KAR timing."
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restore Original", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = {
                            onSaveSync(previewLines)
                            statusBanner = "Saved synchronized timing locally!"
                        },
                        enabled = previewLines.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF9D),
                            contentColor = Color(0xFF040914)
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Sync Locally", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun MelodyAndSyllableTimelineBar(
    durationMs: Long,
    currentPosMs: Long,
    melodyNotes: List<MelodyNote>,
    syllables: List<EditableSyllableItem>,
    primaryColor: Color
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF060914))
    ) {
        val w = size.width
        val h = size.height

        // Draw vocal melody notes in the upper half
        for (note in melodyNotes) {
            val x1 = (note.startMs.toFloat() / safeDuration) * w
            val x2 = max(x1 + 2f, (note.endMs.toFloat() / safeDuration) * w)
            val yRatio = 1f - ((note.midiNote - 48).coerceIn(0, 36) / 36f)
            val y = 2f + yRatio * (h * 0.45f)
            drawLine(
                color = Color(0xFFFF2A85).copy(alpha = 0.65f),
                start = Offset(x1, y),
                end = Offset(x2, y),
                strokeWidth = 3f
            )
        }

        // Draw syllable ticks in the lower half
        for (syl in syllables) {
            val x = (syl.startMs.toFloat() / safeDuration) * w
            drawLine(
                color = primaryColor.copy(alpha = 0.7f),
                start = Offset(x, h * 0.55f),
                end = Offset(x, h),
                strokeWidth = 2f
            )
        }

        // Draw playhead cursor
        val cursorX = ((currentPosMs.toFloat() / safeDuration) * w).coerceIn(0f, w)
        drawLine(
            color = Color(0xFFFFD600),
            start = Offset(cursorX, 0f),
            end = Offset(cursorX, h),
            strokeWidth = 3.dp.toPx()
        )
    }
}

@Composable
private fun TapToSyncPanel(
    items: List<EditableSyllableItem>,
    cursorIndex: Int,
    livePosMs: Long,
    onSelectCursor: (Int) -> Unit,
    onTapStampSyllable: () -> Unit,
    onToggleLineBreakAtCursor: () -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics loaded. Switch to '3. Lyrics Text & Auto-Align' to paste lyrics or import an .LRC file.",
                color = Color(0xFF9FB2D9),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val safeCursor = cursorIndex.coerceIn(0, items.lastIndex)
    val currentTarget = items[safeCursor]

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF101B3B),
            border = BorderStroke(1.5.dp, Color(0xFFFFD600)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NEXT SYLLABLE TO STAMP (${safeCursor + 1} of ${items.size})",
                    color = Color(0xFFFFD600),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "\"${currentTarget.text}\"",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSelectCursor((safeCursor - 1).coerceAtLeast(0)) },
                        enabled = safeCursor > 0
                    ) {
                        Text("← Prev Syllable")
                    }
                    Button(
                        onClick = onTapStampSyllable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00F0FF),
                            contentColor = Color(0xFF040914)
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TAP NOW @ ${formatMsPrecise(livePosMs)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                    OutlinedButton(onClick = onToggleLineBreakAtCursor) {
                        Text(if (currentTarget.beginsLine) "Line Break: ON" else "Line Break: OFF")
                    }
                }
            }
        }

        // Scrollable list of syllables to select cursor
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(items) { idx, item ->
                val isSelected = idx == safeCursor
                Surface(
                    onClick = { onSelectCursor(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF1B2E63) else Color(0xFF0D1428),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00F0FF) else Color(0xFF1E2D56)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.beginsLine) {
                                Text("↵ LINE", color = Color(0xFFFF2A85), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Text(item.text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = "${formatMsPrecise(item.startMs)} → ${formatMsPrecise(item.endMs)}",
                            color = Color(0xFFFFD600),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FineTuneSyllablesPanel(
    items: List<EditableSyllableItem>,
    onSeekTo: (Long) -> Unit,
    onNudgeItem: (Int, Long) -> Unit,
    onShiftAll: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SHIFT ENTIRE SONG:", color = Color(0xFF00F0FF), fontSize = 11.sp, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onShiftAll(-250L) }) { Text("-250ms", fontSize = 11.sp) }
                FilledTonalButton(onClick = { onShiftAll(-50L) }) { Text("-50ms", fontSize = 11.sp) }
                FilledTonalButton(onClick = { onShiftAll(50L) }) { Text("+50ms", fontSize = 11.sp) }
                FilledTonalButton(onClick = { onShiftAll(250L) }) { Text("+250ms", fontSize = 11.sp) }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(items) { idx, item ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0D1428),
                    border = BorderStroke(1.dp, Color(0xFF1E2D56)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSeekTo(item.startMs) }
                        ) {
                            Text(
                                text = "${idx + 1}. ${item.text}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${formatMsPrecise(item.startMs)} – ${formatMsPrecise(item.endMs)} (Tap to Seek)",
                                color = Color(0xFF00F0FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onNudgeItem(idx, -100L) }) { Text("-100", fontSize = 11.sp) }
                            TextButton(onClick = { onNudgeItem(idx, -25L) }) { Text("-25", fontSize = 11.sp) }
                            TextButton(onClick = { onNudgeItem(idx, 25L) }) { Text("+25", fontSize = 11.sp) }
                            TextButton(onClick = { onNudgeItem(idx, 100L) }) { Text("+100", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextAndMelodyAlignPanel(
    rawText: String,
    melodyNoteCount: Int,
    onRawTextChange: (String) -> Unit,
    onLoadTokensForTapSync: () -> Unit,
    onAutoAlignToMelody: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Paste or edit lyrics below (one line per phrase; separate syllables or words with spaces):",
            color = Color(0xFFB5C7E8),
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = rawText,
            onValueChange = onRawTextChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = { Text("Song Lyrics Text") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onLoadTokensForTapSync,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Load for Tap-to-Sync", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = onAutoAlignToMelody,
                enabled = melodyNoteCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFFFD600))
                Spacer(Modifier.width(6.dp))
                Text("Auto-Align to Melody ($melodyNoteCount notes)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Audio, Video & Bluetooth Latency Calibration Dialog.
 * Changes take effect immediately on the live playback clock.
 */
@Composable
fun LatencyCalibrationDialog(
    songTitle: String?,
    calibration: LatencyCalibration,
    onUpdateGlobalOffset: (Long) -> Unit,
    onUpdatePerSongOffset: (Long) -> Unit,
    onUpdateBluetoothOffset: (Long) -> Unit,
    onToggleBluetoothAuto: (Boolean) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0C1226),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFDCE7FF),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00F0FF))
                Text("Audio & Lyrics Latency Calibration", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131E3E),
                    border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "TOTAL EFFECTIVE SYNC OFFSET: ${formatSignedMs(calibration.totalEffectiveOffsetMs)}",
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Hardware Audio Buffer: ${calibration.audioBufferLatencyMs} ms (auto-compensated) • " +
                                if (calibration.bluetoothOutputDetected) "Bluetooth Speaker/Headset Active 🎧" else "Wired/Internal Speaker Active 🔊",
                            color = Color(0xFFFFD600),
                            fontSize = 11.sp
                        )
                    }
                }

                // 1. Per-Song Timing Offset
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Per-Song Offset (${songTitle?.take(22) ?: "Current Song"})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = formatSignedMs(calibration.perSongOffsetMs),
                            color = Color(0xFF00F0FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        "Positive (+) makes lyrics appear earlier; Negative (-) delays lyrics.",
                        fontSize = 11.sp,
                        color = Color(0xFF9FB2D9)
                    )
                    Slider(
                        value = calibration.perSongOffsetMs.toFloat(),
                        onValueChange = { onUpdatePerSongOffset((it / 10f).toLong() * 10L) },
                        valueRange = -2000f..2000f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { onUpdatePerSongOffset(calibration.perSongOffsetMs - 100L) }) { Text("-100ms") }
                        TextButton(onClick = { onUpdatePerSongOffset(calibration.perSongOffsetMs - 25L) }) { Text("-25ms") }
                        TextButton(onClick = { onUpdatePerSongOffset(0L) }) { Text("0ms") }
                        TextButton(onClick = { onUpdatePerSongOffset(calibration.perSongOffsetMs + 25L) }) { Text("+25ms") }
                        TextButton(onClick = { onUpdatePerSongOffset(calibration.perSongOffsetMs + 100L) }) { Text("+100ms") }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // 2. Global Audio/Lyrics Offset
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Global Audio/Lyrics Offset", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(formatSignedMs(calibration.globalOffsetMs), color = Color(0xFFFFD600), fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    Slider(
                        value = calibration.globalOffsetMs.toFloat(),
                        onValueChange = { onUpdateGlobalOffset((it / 10f).toLong() * 10L) },
                        valueRange = -1000f..1000f
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // 3. Bluetooth Audio Latency Compensation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = Color(0xFF00FF9D))
                        Column {
                            Text("Bluetooth Latency Compensation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Auto-apply when Bluetooth audio is connected", fontSize = 11.sp, color = Color(0xFF9FB2D9))
                        }
                    }
                    Switch(checked = calibration.bluetoothAutoCompensation, onCheckedChange = onToggleBluetoothAuto)
                }
                if (calibration.bluetoothAutoCompensation) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bluetooth Delay Offset", fontSize = 12.sp, color = Color.White)
                        Text("+${calibration.bluetoothOffsetMs} ms", fontSize = 12.sp, color = Color(0xFF00FF9D), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = calibration.bluetoothOffsetMs.toFloat(),
                        onValueChange = { onUpdateBluetoothOffset((it / 10f).toLong() * 10L) },
                        valueRange = 0f..600f
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onResetAll) { Text("Reset All to 0ms") }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

private fun flattenLinesToEditable(lines: List<LyricLine>): List<EditableSyllableItem> {
    val out = ArrayList<EditableSyllableItem>()
    for (line in lines) {
        for ((idx, syl) in line.syllables.withIndex()) {
            out.add(
                EditableSyllableItem(
                    text = syl.text,
                    startMs = syl.startMs,
                    endMs = syl.endMs,
                    beginsLine = idx == 0,
                    endsWord = syl.endsWord
                )
            )
        }
    }
    return out
}

private fun rebuildLinesFromEditable(
    items: List<EditableSyllableItem>,
    melodyNotes: List<MelodyNote>,
    durationMs: Long
): List<LyricLine> {
    if (items.isEmpty()) return emptyList()
    val events = items.mapIndexed { idx, item ->
        com.jktv.offline.playback.TimedLyricEvent(
            timeMs = item.startMs,
            text = item.text,
            beginsLine = idx == 0 || item.beginsLine,
            endsWord = item.endsWord
        )
    }
    return LyricsSyncEngine.buildLinesFromEvents(events, melodyNotes, durationMs)
}

private fun parseRawTextToEditable(rawText: String, durationMs: Long): List<EditableSyllableItem> {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return emptyList()
    val totalTokens = lines.sumOf { it.split(Regex("\\s+")).count { t -> t.isNotBlank() } }.coerceAtLeast(1)
    val safeSpan = durationMs.coerceAtLeast(10_000L)
    val stepMs = (safeSpan / (totalTokens + 1)).coerceIn(250L, 1500L)
    val out = ArrayList<EditableSyllableItem>(totalTokens)
    var index = 0
    for (line in lines) {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        for ((tIdx, tok) in tokens.withIndex()) {
            val start = (index + 1) * stepMs
            val continuesWord = tok.endsWith("-") && tok.length > 1
            val cleanTok = if (continuesWord) tok.removeSuffix("-") else tok
            out.add(
                EditableSyllableItem(
                    text = cleanTok,
                    startMs = start,
                    endMs = start + stepMs - 40L,
                    beginsLine = tIdx == 0,
                    endsWord = !continuesWord
                )
            )
            index++
        }
    }
    return out
}

private fun formatMsPrecise(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val mins = safe / 60_000L
    val secs = (safe % 60_000L) / 1000L
    val hundredths = (safe % 1000L) / 10L
    return String.format(Locale.ROOT, "%02d:%02d.%02d", mins, secs, hundredths)
}

private fun formatSignedMs(ms: Long): String =
    if (ms > 0L) "+${ms} ms" else "${ms} ms"
