package com.jktv.offline.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class SyncSourceType(val badgeText: String, val isAuthoritative: Boolean) {
    USER_SYNCED_SIDECAR("CUSTOM SYNCED", true),
    EMBEDDED_MIDI_KAR("KAR/MIDI SYNC", true),
    EXTERNAL_LRC("LRC TIMED", true),
    MELODY_AUTO_ALIGNED("MELODY AUTO-ALIGNED", false),
    UNTIMED_TEXT("UNTIMED LYRICS", false),
    NO_LYRICS("NO LYRIC TIMING", false)
}

enum class SyncConfidence(val description: String) {
    ACCURATE_SYLLABLE("Accurate syllable-by-syllable timing"),
    LINE_LEVEL("Line-level timing (syllables bounded by line)"),
    ESTIMATED_MELODY("Automatically aligned to MIDI vocal melody notes (verify in Sync Editor)"),
    UNSYNCHRONIZED("No reliable timing data in source — use Sync Editor to synchronize")
}

data class LatencyCalibration(
    val globalOffsetMs: Long = 0L,
    val perSongOffsetMs: Long = 0L,
    val bluetoothOffsetMs: Long = 180L,
    val bluetoothAutoCompensation: Boolean = true,
    val bluetoothOutputDetected: Boolean = false,
    val audioBufferLatencyMs: Long = 23L
) {
    /**
     * Positive offset advances lyrics relative to audio (compensating for Bluetooth/audio output delay).
     * Negative offset delays lyrics.
     */
    val totalEffectiveOffsetMs: Long
        get() = globalOffsetMs + perSongOffsetMs +
            (if (bluetoothAutoCompensation && bluetoothOutputDetected) bluetoothOffsetMs else 0L)
}

data class ResolvedLyricsSync(
    val lyricLines: List<LyricLine>,
    val flatEvents: List<TimedLyricEvent>,
    val sourceType: SyncSourceType,
    val confidence: SyncConfidence,
    val perSongOffsetMs: Long = 0L
)

/**
 * Intelligent 5-Tier Lyrics Synchronization, Sidecar Storage, LRC Parser/Exporter,
 * Melody-to-Syllable Aligner, and Audio/Bluetooth Latency Calibrator.
 */
object LyricsSyncEngine {

    private val lineTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTagRegex = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]+)""")

    fun isBluetoothAudioActive(context: Context): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.any { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            }
        }.getOrDefault(false)
    }

    /**
     * Resolves synchronized lyrics using strict 5-tier priority:
     * 1. Saved user custom sync sidecar (`karaoke/lyrics-sync/song_<id>.jktvsync.json`)
     * 2. Embedded KAR/MIDI syllable timing events (refined with vocal MelodyNote note-off bounds)
     * 3. External `.lrc` or `.jktvsync.json` sidecar file next to the MIDI/KAR file
     * 4. Automatic Melody-Note alignment when untimed text and vocal `MelodyNote`s exist
     * 5. Explicit `NO_LYRICS` / `UNTIMED_TEXT` status (never fabricates fake accuracy)
     */
    fun resolveForSong(
        context: Context?,
        songId: Long,
        midiFile: File?,
        parsedTimeline: MidiTimeline
    ): ResolvedLyricsSync {
        // Tier 1: User-saved custom sync sidecar in app storage
        if (context != null && songId > 0L) {
            val customFile = getCustomSyncFile(context, songId)
            if (customFile.isFile && customFile.length() > 0L) {
                val loaded = runCatching { parseJktvSyncJson(customFile.readText(Charsets.UTF_8)) }.getOrNull()
                if (loaded != null && loaded.lyricLines.isNotEmpty()) {
                    return loaded.copy(sourceType = SyncSourceType.USER_SYNCED_SIDECAR)
                }
            }
        }

        // Tier 2: Embedded KAR/MIDI timed lyrics
        if (parsedTimeline.lyricLines.isNotEmpty() && parsedTimeline.hasEventTiming) {
            val refinedLines = refineSyllableEndingsWithMelodyNotes(
                lines = parsedTimeline.lyricLines,
                melodyNotes = parsedTimeline.melodyNotes,
                durationMs = parsedTimeline.durationMs
            )
            return ResolvedLyricsSync(
                lyricLines = refinedLines,
                flatEvents = parsedTimeline.lyrics,
                sourceType = SyncSourceType.EMBEDDED_MIDI_KAR,
                confidence = SyncConfidence.ACCURATE_SYLLABLE
            )
        }

        // Tier 3: External synchronized .lrc or .jktvsync.json alongside the song file
        if (midiFile != null) {
            val parent = midiFile.parentFile
            val stem = midiFile.nameWithoutExtension
            if (parent != null) {
                val sidecarJson = File(parent, "$stem.jktvsync.json")
                if (sidecarJson.isFile) {
                    val loaded = runCatching { parseJktvSyncJson(sidecarJson.readText(Charsets.UTF_8)) }.getOrNull()
                    if (loaded != null && loaded.lyricLines.isNotEmpty()) {
                        return loaded
                    }
                }
                val lrcFile = File(parent, "$stem.lrc")
                if (lrcFile.isFile) {
                    val loaded = runCatching {
                        parseLrc(lrcFile.readText(Charsets.UTF_8), parsedTimeline.durationMs, parsedTimeline.melodyNotes)
                    }.getOrNull()
                    if (loaded != null && loaded.lyricLines.isNotEmpty()) {
                        return loaded
                    }
                }
            }
        }

        // Tier 4: Embedded untimed lyrics + Automatic Melody-Note Alignment
        if (parsedTimeline.lyrics.isNotEmpty() && !parsedTimeline.hasEventTiming) {
            val rawTokens = parsedTimeline.lyrics.flatMap { it.text.split(Regex("\\s+")) }.filter { it.isNotBlank() }
            if (parsedTimeline.melodyNotes.size >= max(4, rawTokens.size / 2)) {
                val aligned = autoAlignTokensToMelodyNotes(
                    tokens = rawTokens,
                    melodyNotes = parsedTimeline.melodyNotes,
                    durationMs = parsedTimeline.durationMs
                )
                return ResolvedLyricsSync(
                    lyricLines = aligned,
                    flatEvents = aligned.flatMap { line ->
                        line.syllables.mapIndexed { idx, syl ->
                            TimedLyricEvent(
                                timeMs = syl.startMs,
                                text = syl.text,
                                beginsLine = idx == 0,
                                endsWord = syl.endsWord
                            )
                        }
                    },
                    sourceType = SyncSourceType.MELODY_AUTO_ALIGNED,
                    confidence = SyncConfidence.ESTIMATED_MELODY
                )
            }
            return ResolvedLyricsSync(
                lyricLines = parsedTimeline.lyricLines,
                flatEvents = parsedTimeline.lyrics,
                sourceType = SyncSourceType.UNTIMED_TEXT,
                confidence = SyncConfidence.UNSYNCHRONIZED
            )
        }

        // Tier 5: No lyrics present — report honestly and allow manual editor
        return ResolvedLyricsSync(
            lyricLines = emptyList(),
            flatEvents = emptyList(),
            sourceType = SyncSourceType.NO_LYRICS,
            confidence = SyncConfidence.UNSYNCHRONIZED
        )
    }

    /**
     * Uses actual vocal MelodyNote note-off timestamps (when a melody note starts at the same time
     * as a syllable) to determine the exact musical duration of sustained syllables, rather than
     * guessing from text length. Also updates reconstructed [LyricWord] end timestamps.
     */
    fun refineSyllableEndingsWithMelodyNotes(
        lines: List<LyricLine>,
        melodyNotes: List<MelodyNote>,
        durationMs: Long
    ): List<LyricLine> {
        if (lines.isEmpty() || melodyNotes.isEmpty()) return lines
        return lines.mapIndexed { lineIdx, line ->
            val nextLineStart = lines.getOrNull(lineIdx + 1)?.startMs ?: durationMs
            val refinedSyllables = line.syllables.mapIndexed { sylIdx, syl ->
                val nextSylStart = line.syllables.getOrNull(sylIdx + 1)?.startMs ?: nextLineStart
                // Find a melody note whose onset aligns closely with this syllable's startMs
                val matchingNote = findMelodyNoteNear(melodyNotes, syl.startMs, toleranceMs = 110L)
                val musicalEnd = if (matchingNote != null && matchingNote.endMs > syl.startMs) {
                    min(matchingNote.endMs, nextSylStart)
                } else {
                    min(syl.endMs, nextSylStart)
                }
                val clampedEnd = max(syl.startMs + 60L, musicalEnd)
                syl.copy(endMs = clampedEnd)
            }
            val lineEnd = max(line.startMs + 100L, refinedSyllables.lastOrNull()?.endMs ?: line.endMs)
            val refinedWords = KaraokeMidiParser.reconstructWordsFromSyllables(line.text, refinedSyllables)
            line.copy(
                endMs = lineEnd,
                syllables = refinedSyllables,
                words = refinedWords
            )
        }
    }

    private fun findMelodyNoteNear(notes: List<MelodyNote>, targetMs: Long, toleranceMs: Long): MelodyNote? {
        var low = 0
        var high = notes.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val n = notes[mid]
            val diff = n.startMs - targetMs
            if (kotlin.math.abs(diff) <= toleranceMs) return n
            if (n.startMs < targetMs) low = mid + 1 else high = mid - 1
        }
        return null
    }

    /**
     * Automatically maps a list of lyric words/syllables onto extracted vocal MelodyNote events.
     */
    fun autoAlignTokensToMelodyNotes(
        tokens: List<String>,
        melodyNotes: List<MelodyNote>,
        durationMs: Long
    ): List<LyricLine> {
        if (tokens.isEmpty() || melodyNotes.isEmpty()) return emptyList()
        val timedEvents = ArrayList<TimedLyricEvent>(tokens.size)
        val noteCount = melodyNotes.size
        for (i in tokens.indices) {
            val mappedNoteIdx = ((i.toLong() * noteCount) / tokens.size.coerceAtLeast(1))
                .toInt()
                .coerceIn(0, noteCount - 1)
            val note = melodyNotes[mappedNoteIdx]
            val prevNote = if (mappedNoteIdx > 0) melodyNotes[mappedNoteIdx - 1] else null
            val startsLine = i == 0 || (i % 7 == 0) ||
                (prevNote != null && note.startMs - prevNote.endMs >= 1600L)
            val rawTok = tokens[i]
            val continuesWord = rawTok.endsWith("-") && rawTok.length > 1
            val cleanTok = if (continuesWord) rawTok.removeSuffix("-") else rawTok
            timedEvents.add(
                TimedLyricEvent(
                    timeMs = note.startMs,
                    text = cleanTok,
                    beginsLine = startsLine,
                    endsWord = !continuesWord
                )
            )
        }
        return buildLinesFromEvents(timedEvents, melodyNotes, durationMs)
    }

    fun buildLinesFromEvents(
        events: List<TimedLyricEvent>,
        melodyNotes: List<MelodyNote>,
        durationMs: Long
    ): List<LyricLine> {
        if (events.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<TimedLyricEvent>>()
        var current = mutableListOf<TimedLyricEvent>()
        for (ev in events) {
            val prev = current.lastOrNull()
            val canBreakHere = prev == null || prev.endsWord
            val len = current.sumOf { it.text.length + (if (it.endsWord) 1 else 0) }
            if (current.isNotEmpty() && canBreakHere && (ev.beginsLine || len + ev.text.length > 44)) {
                groups.add(current)
                current = mutableListOf()
            }
            current.add(ev)
        }
        if (current.isNotEmpty()) groups.add(current)

        val rawLines = groups.mapIndexed { lineIdx, group ->
            val nextLineStart = groups.getOrNull(lineIdx + 1)?.firstOrNull()?.timeMs ?: durationMs
            val sb = StringBuilder()
            val syllables = ArrayList<LyricSyllable>(group.size)
            for (i in group.indices) {
                val item = group[i]
                val prevItem = group.getOrNull(i - 1)
                val nextStart = group.getOrNull(i + 1)?.timeMs ?: min(nextLineStart, item.timeMs + 1600L)
                val endMs = max(item.timeMs + 80L, min(nextStart, item.timeMs + 2400L))
                val startsWithHyphen = item.text.startsWith("-") && item.text.length > 1
                val endsWithHyphen = item.text.endsWith("-") && item.text.length > 1
                val prevEndedWord = prevItem != null && prevItem.endsWord && !startsWithHyphen && !sb.endsWith("-")
                if (sb.isNotEmpty() && prevEndedWord) {
                    sb.append(' ')
                }
                val cStart = sb.length
                val token = item.text.removePrefix("-").removeSuffix("-").ifBlank { item.text }
                sb.append(token)
                val cEnd = sb.length
                val isLast = i == group.lastIndex
                val effectiveEndsWord = if (isLast) true else (item.endsWord && !endsWithHyphen)
                syllables.add(LyricSyllable(token, item.timeMs, endMs, cStart, cEnd, effectiveEndsWord))
            }
            val lineText = sb.toString().trim()
            LyricLine(
                index = lineIdx,
                startMs = syllables.first().startMs,
                endMs = syllables.last().endMs,
                text = lineText,
                syllables = syllables,
                words = KaraokeMidiParser.reconstructWordsFromSyllables(lineText, syllables)
            )
        }
        return refineSyllableEndingsWithMelodyNotes(rawLines, melodyNotes, durationMs)
    }

    /**
     * Parses both standard LRC (`[mm:ss.xx]Line text`) and enhanced syllable/word LRC
     * (`[mm:ss.xx]<mm:ss.xx>Syl1<mm:ss.xx>Syl2 <mm:ss.xx>Word2`), preserving intra-word
     * syllable attachments when no space separates consecutive `<mm:ss.xx>` tags.
     */
    fun parseLrc(
        lrcContent: String,
        durationMs: Long,
        melodyNotes: List<MelodyNote> = emptyList()
    ): ResolvedLyricsSync {
        val rawLines = lrcContent.lines()
        val parsedLines = mutableListOf<LyricLine>()
        val flatEvents = mutableListOf<TimedLyricEvent>()
        var hasWordLevelTags = false
        var offsetTagMs = 0L

        data class PendingLine(
            val startMs: Long,
            val rawBody: String
        )
        val pending = mutableListOf<PendingLine>()

        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[offset:", ignoreCase = true)) {
                val value = trimmed.removePrefix("[offset:").removePrefix("[OFFSET:").substringBefore(']').trim()
                offsetTagMs = value.toLongOrNull() ?: 0L
                continue
            }
            val matches = lineTagRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue
            val body = trimmed.substring(matches.last().range.last + 1).trim()
            if (body.isBlank()) continue
            for (m in matches) {
                val ts = (parseLrcTimestamp(m.groupValues[1], m.groupValues[2], m.groupValues[3]) - offsetTagMs)
                    .coerceAtLeast(0L)
                pending.add(PendingLine(ts, body))
            }
        }

        pending.sortBy { it.startMs }
        for (i in pending.indices) {
            val item = pending[i]
            val nextLineStart = pending.getOrNull(i + 1)?.startMs ?: max(durationMs, item.startMs + 3000L)
            val wordMatches = wordTagRegex.findAll(item.rawBody).toList()

            if (wordMatches.isNotEmpty()) {
                hasWordLevelTags = true
                val sb = StringBuilder()
                val syllables = ArrayList<LyricSyllable>(wordMatches.size)
                var prevEndsWord = true
                for (wIdx in wordMatches.indices) {
                    val wm = wordMatches[wIdx]
                    val wStart = (parseLrcTimestamp(wm.groupValues[1], wm.groupValues[2], wm.groupValues[3]) - offsetTagMs)
                        .coerceAtLeast(item.startMs)
                    val nextWStart = if (wIdx + 1 < wordMatches.size) {
                        val nm = wordMatches[wIdx + 1]
                        (parseLrcTimestamp(nm.groupValues[1], nm.groupValues[2], nm.groupValues[3]) - offsetTagMs)
                            .coerceAtLeast(wStart + 60L)
                    } else {
                        min(nextLineStart, wStart + 1800L)
                    }
                    val rawChunk = wm.groupValues[4]
                    val hasLeadSpace = rawChunk.firstOrNull()?.isWhitespace() == true
                    val hasTrailSpace = rawChunk.lastOrNull()?.isWhitespace() == true
                    val token = rawChunk.trim()
                    if (token.isEmpty()) {
                        if (hasLeadSpace || hasTrailSpace) prevEndsWord = true
                        continue
                    }
                    if (sb.isNotEmpty() && (prevEndsWord || hasLeadSpace)) {
                        sb.append(' ')
                    }
                    val cStart = sb.length
                    sb.append(token)
                    val cEnd = sb.length
                    val wEnd = max(wStart + 60L, nextWStart)
                    val isLast = wIdx == wordMatches.lastIndex
                    val endsWord = isLast || hasTrailSpace
                    syllables.add(LyricSyllable(token, wStart, wEnd, cStart, cEnd, endsWord))
                    flatEvents.add(TimedLyricEvent(wStart, token, syllables.size == 1, endsWord))
                    prevEndsWord = endsWord
                }
                if (syllables.isNotEmpty()) {
                    val lineText = sb.toString()
                    parsedLines.add(
                        LyricLine(
                            index = parsedLines.size,
                            startMs = syllables.first().startMs,
                            endMs = syllables.last().endMs,
                            text = lineText,
                            syllables = syllables,
                            words = KaraokeMidiParser.reconstructWordsFromSyllables(lineText, syllables)
                        )
                    )
                }
            } else {
                // Line-level LRC: align words to melody notes inside [startMs, nextLineStart]
                val lineEnd = max(item.startMs + 200L, min(nextLineStart, item.startMs + 6000L))
                val words = item.rawBody.split(Regex("\\s+")).filter { it.isNotBlank() }
                val notesInLine = melodyNotes.filter { it.startMs in (item.startMs - 120L)..(lineEnd + 120L) }
                val syllables = if (words.size > 1 && notesInLine.size >= words.size) {
                    val sb = StringBuilder()
                    words.mapIndexed { wIdx, w ->
                        val note = notesInLine[(wIdx * notesInLine.size) / words.size]
                        val nextNoteStart = notesInLine.getOrNull(((wIdx + 1) * notesInLine.size) / words.size)?.startMs
                            ?: lineEnd
                        if (sb.isNotEmpty()) sb.append(' ')
                        val cStart = sb.length
                        sb.append(w)
                        val cEnd = sb.length
                        LyricSyllable(w, note.startMs, max(note.startMs + 60L, min(note.endMs, nextNoteStart)), cStart, cEnd, true)
                    }
                } else {
                    val sb = StringBuilder()
                    val span = (lineEnd - item.startMs).coerceAtLeast(100L)
                    words.mapIndexed { wIdx, w ->
                        val wStart = item.startMs + (span * wIdx) / words.size.coerceAtLeast(1)
                        val wEnd = item.startMs + (span * (wIdx + 1)) / words.size.coerceAtLeast(1)
                        if (sb.isNotEmpty()) sb.append(' ')
                        val cStart = sb.length
                        sb.append(w)
                        val cEnd = sb.length
                        LyricSyllable(w, wStart, max(wStart + 60L, wEnd), cStart, cEnd, true)
                    }
                }
                flatEvents.add(TimedLyricEvent(item.startMs, item.rawBody, true, true))
                parsedLines.add(
                    LyricLine(
                        index = parsedLines.size,
                        startMs = item.startMs,
                        endMs = lineEnd,
                        text = item.rawBody,
                        syllables = syllables,
                        words = KaraokeMidiParser.reconstructWordsFromSyllables(item.rawBody, syllables)
                    )
                )
            }
        }

        return ResolvedLyricsSync(
            lyricLines = parsedLines,
            flatEvents = flatEvents,
            sourceType = SyncSourceType.EXTERNAL_LRC,
            confidence = if (hasWordLevelTags) SyncConfidence.ACCURATE_SYLLABLE else SyncConfidence.LINE_LEVEL,
            perSongOffsetMs = offsetTagMs
        )
    }

    /**
     * Exports synchronized lyrics to Enhanced Karaoke LRC format (`[mm:ss.xx]<mm:ss.xx>Syl1<mm:ss.xx>Syl2 ...`),
     * inserting spaces only between words (`endsWord == true`).
     */
    fun exportEnhancedLrc(songTitle: String, artist: String, lines: List<LyricLine>, offsetMs: Long = 0L): String {
        val sb = StringBuilder()
        sb.appendLine("[ti:$songTitle]")
        if (artist.isNotBlank()) sb.appendLine("[ar:$artist]")
        sb.appendLine("[by:JKTV Offline Karaoke Sync Engine]")
        if (offsetMs != 0L) sb.appendLine("[offset:$offsetMs]")
        sb.appendLine()
        for (line in lines) {
            sb.append(formatLrcBracketTag(line.startMs))
            for ((idx, syl) in line.syllables.withIndex()) {
                if (idx > 0 && line.syllables[idx - 1].endsWord) {
                    sb.append(' ')
                }
                sb.append(formatLrcAngleTag(syl.startMs)).append(syl.text)
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun exportJktvSyncJson(lines: List<LyricLine>, perSongOffsetMs: Long = 0L): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"format\": \"jktv-sync-v1\",\n")
        sb.append("  \"perSongOffsetMs\": ").append(perSongOffsetMs).append(",\n")
        sb.append("  \"lines\": [\n")
        for ((lIdx, line) in lines.withIndex()) {
            sb.append("    {\n")
            sb.append("      \"index\": ").append(line.index).append(",\n")
            sb.append("      \"startMs\": ").append(line.startMs).append(",\n")
            sb.append("      \"endMs\": ").append(line.endMs).append(",\n")
            sb.append("      \"text\": \"").append(escapeJson(line.text)).append("\",\n")
            sb.append("      \"syllables\": [\n")
            for ((sIdx, syl) in line.syllables.withIndex()) {
                sb.append("        {\"text\": \"").append(escapeJson(syl.text)).append("\", ")
                sb.append("\"startMs\": ").append(syl.startMs).append(", ")
                sb.append("\"endMs\": ").append(syl.endMs).append(", ")
                sb.append("\"charStart\": ").append(syl.charStart).append(", ")
                sb.append("\"charEnd\": ").append(syl.charEnd).append(", ")
                sb.append("\"endsWord\": ").append(syl.endsWord).append("}")
                if (sIdx < line.syllables.lastIndex) sb.append(',')
                sb.append('\n')
            }
            sb.append("      ]\n")
            sb.append("    }")
            if (lIdx < lines.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun parseJktvSyncJson(jsonText: String): ResolvedLyricsSync {
        val offsetMatch = Regex(""""perSongOffsetMs"\s*:\s*(-?\d+)""").find(jsonText)
        val offset = offsetMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val sylRegex = Regex(
            """\{\s*"text"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"startMs"\s*:\s*(-?\d+)\s*,\s*"endMs"\s*:\s*(-?\d+)\s*,\s*"charStart"\s*:\s*(\d+)\s*,\s*"charEnd"\s*:\s*(\d+)(?:\s*,\s*"endsWord"\s*:\s*(true|false))?\s*\}"""
        )
        val syllablesBlockRegex = Regex(""""syllables"\s*:\s*\[([^\]]*)]""")
        val lines = ArrayList<LyricLine>()
        val flat = ArrayList<TimedLyricEvent>()

        for (blockMatch in syllablesBlockRegex.findAll(jsonText)) {
            val block = blockMatch.groupValues[1]
            val sylMatches = sylRegex.findAll(block).toList()
            if (sylMatches.isEmpty()) continue
            val syllables = ArrayList<LyricSyllable>(sylMatches.size)
            val sb = StringBuilder()

            data class RawJsonSyl(
                val txt: String,
                val sMs: Long,
                val eMs: Long,
                val origCharStart: Int,
                val origCharEnd: Int,
                val explicitEndsWord: Boolean?
            )

            val parsedRaw = sylMatches.mapNotNull { sm ->
                val txt = unescapeJson(sm.groupValues[1]).trim()
                if (txt.isEmpty()) null
                else {
                    val sMs = sm.groupValues[2].toLongOrNull() ?: 0L
                    val eMs = max(sMs + 60L, sm.groupValues[3].toLongOrNull() ?: (sMs + 400L))
                    val cStart = sm.groupValues[4].toIntOrNull() ?: 0
                    val cEnd = sm.groupValues[5].toIntOrNull() ?: (cStart + txt.length)
                    val endsW = sm.groupValues.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toBooleanStrictOrNull()
                    RawJsonSyl(txt, sMs, eMs, cStart, cEnd, endsW)
                }
            }

            for (j in parsedRaw.indices) {
                val cur = parsedRaw[j]
                val prev = parsedRaw.getOrNull(j - 1)
                val next = parsedRaw.getOrNull(j + 1)
                val prevEndedWord = prev == null || (prev.explicitEndsWord ?: (cur.origCharStart > prev.origCharEnd))
                if (sb.isNotEmpty() && prevEndedWord) {
                    sb.append(' ')
                }
                val cStart = sb.length
                sb.append(cur.txt)
                val cEnd = sb.length
                val endsWord = if (next == null) {
                    true
                } else {
                    cur.explicitEndsWord ?: (next.origCharStart > cur.origCharEnd)
                }
                syllables.add(LyricSyllable(cur.txt, cur.sMs, cur.eMs, cStart, cEnd, endsWord))
                flat.add(TimedLyricEvent(cur.sMs, cur.txt, j == 0, endsWord))
            }
            if (syllables.isNotEmpty()) {
                val lineText = sb.toString()
                lines.add(
                    LyricLine(
                        index = lines.size,
                        startMs = syllables.first().startMs,
                        endMs = syllables.last().endMs,
                        text = lineText,
                        syllables = syllables,
                        words = KaraokeMidiParser.reconstructWordsFromSyllables(lineText, syllables)
                    )
                )
            }
        }
        return ResolvedLyricsSync(
            lyricLines = lines,
            flatEvents = flat,
            sourceType = SyncSourceType.USER_SYNCED_SIDECAR,
            confidence = SyncConfidence.ACCURATE_SYLLABLE,
            perSongOffsetMs = offset
        )
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeJson(value: String): String =
        value.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")

    fun saveCustomSyncForSong(
        context: Context,
        songId: Long,
        lines: List<LyricLine>,
        perSongOffsetMs: Long = 0L
    ): File {
        val file = getCustomSyncFile(context, songId)
        file.parentFile?.mkdirs()
        file.writeText(exportJktvSyncJson(lines, perSongOffsetMs), Charsets.UTF_8)
        return file
    }

    fun deleteCustomSyncForSong(context: Context, songId: Long): Boolean {
        val file = getCustomSyncFile(context, songId)
        return if (file.exists()) file.delete() else false
    }

    fun getCustomSyncFile(context: Context, songId: Long): File =
        File(context.filesDir, "karaoke/lyrics-sync/song_$songId.jktvsync.json")

    private fun parseLrcTimestamp(minStr: String, secStr: String, fracStr: String): Long {
        val mins = minStr.toLongOrNull() ?: 0L
        val secs = secStr.toLongOrNull() ?: 0L
        val millis = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return mins * 60_000L + secs * 1_000L + millis
    }

    private fun formatLrcBracketTag(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val mins = safe / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val hundredths = (safe % 1000L) / 10L
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", mins, secs, hundredths)
    }

    private fun formatLrcAngleTag(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val mins = safe / 60_000L
        val secs = (safe % 60_000L) / 1000L
        val hundredths = (safe % 1000L) / 10L
        return String.format(Locale.ROOT, "<%02d:%02d.%02d>", mins, secs, hundredths)
    }
}
