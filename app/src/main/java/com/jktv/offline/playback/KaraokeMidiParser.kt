package com.jktv.offline.playback

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.abs

data class TimedLyricEvent(
    val timeMs: Long,
    val text: String,
    val beginsLine: Boolean,
    val endsWord: Boolean = true,
    val beginsParagraph: Boolean = false
)

data class LyricSyllable(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charStart: Int,
    val charEnd: Int,
    val endsWord: Boolean = true
)

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charStart: Int,
    val charEnd: Int,
    val syllables: List<LyricSyllable>
)

data class LyricLine(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<LyricSyllable>,
    val words: List<LyricWord> = KaraokeMidiParser.reconstructWordsFromSyllables(text, syllables)
)

data class MelodyNote(
    val startMs: Long,
    val endMs: Long,
    val midiNote: Int,
    val velocity: Int
)

enum class MidiEventType {
    NOTE_ON,
    NOTE_OFF,
    PROGRAM_CHANGE,
    CONTROL_CHANGE,
    PITCH_BEND,
    CHANNEL_PRESSURE,
    KEY_PRESSURE
}

data class SequencerMidiEvent(
    val tick: Long,
    val timeUs: Long,
    val timeMs: Long,
    val type: MidiEventType,
    val channel: Int,
    val data1: Int,
    val data2: Int
)

data class MidiTimeline(
    val durationMs: Long,
    val lyrics: List<TimedLyricEvent>,
    val hasLyrics: Boolean,
    val hasEventTiming: Boolean,
    val lyricLines: List<LyricLine> = emptyList(),
    val midiEvents: List<SequencerMidiEvent> = emptyList(),
    val melodyNotes: List<MelodyNote> = emptyList(),
    val melodyChannel: Int = 0,
    val bassChannels: Set<Int> = emptySet(),
    val initialPrograms: IntArray = IntArray(16) { 0 },
    val bpm: Int = 120
) {
    /**
     * Returns the target seek position in ms if currently inside a long intro or instrumental interlude
     * (>= 7.5 seconds before the next vocal line), or null if vocals are active or imminent.
     */
    fun interludeSkipTargetMs(positionMs: Long): Long? {
        if (lyricLines.isEmpty()) return null
        val firstLineStart = lyricLines.first().startMs
        if (positionMs < firstLineStart - 4500L && firstLineStart >= 7500L) {
            return (firstLineStart - 2200L).coerceAtLeast(0L)
        }
        for (i in 0 until lyricLines.size - 1) {
            val currentEnd = lyricLines[i].endMs
            val nextStart = lyricLines[i + 1].startMs
            if (positionMs in (currentEnd + 600L)..(nextStart - 4500L) && (nextStart - currentEnd) >= 8500L) {
                return (nextStart - 2200L).coerceAtLeast(currentEnd)
            }
        }
        return null
    }
}

/**
 * High-speed Standard MIDI (.mid) and Karaoke (.kar) parser.
 * Extracts tempo maps, syllable-timed lyrics (with UTF-8 and Windows-1252 fallback),
 * accurate KAR word/syllable continuation boundaries, natural line reconstruction,
 * real-time sequencer events, melody channel detection, and vocal pitch notes in a single pass.
 */
object KaraokeMidiParser {
    private data class Tempo(val tick: Long, val microsPerQuarter: Int)

    private data class RawLyric(
        val trackIndex: Int,
        val order: Int,
        val tick: Long,
        val rawText: String,
        val metaType: Int,
        val isKarMeta: Boolean
    )

    private data class NormalizedToken(
        val tick: Long,
        val timeMs: Long,
        val text: String,
        val hasLeadingSpace: Boolean,
        val hasTrailingSpace: Boolean,
        val leadingLineBreak: Boolean,
        val trailingLineBreak: Boolean,
        val leadingParagraphBreak: Boolean,
        val trailingParagraphBreak: Boolean,
        val isHyphenPrefix: Boolean
    )

    private data class RawChannelEvent(
        val tick: Long,
        val order: Int,
        val type: MidiEventType,
        val channel: Int,
        val data1: Int,
        val data2: Int
    )

    private data class Cursor(var position: Int)

    private val windows1252: Charset = runCatching { Charset.forName("windows-1252") }
        .getOrElse { Charsets.ISO_8859_1 }

    // Words that frequently end a short 16-char LCD sub-phrase when the sentence continues on the next event
    private val continuationTailWords = setOf(
        "a", "an", "the", "to", "of", "in", "on", "at", "for", "with", "from", "by", "into",
        "and", "or", "but", "nor", "so", "yet", "as", "if", "than", "that", "when", "while",
        "where", "who", "whom", "whose", "which", "what", "how",
        "i", "you", "he", "she", "we", "they", "it",
        "my", "your", "his", "her", "our", "their", "its",
        "am", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did",
        "will", "would", "shall", "should", "can", "could", "may", "might", "must",
        "i'm", "i've", "i'll", "i'd", "you're", "you've", "you'll", "we're", "they're",
        "don't", "can't", "won't", "didn't", "isn't", "aren't", "wasn't", "weren't",
        "really", "finally", "never", "always", "just", "only", "even", "still", "very", "too",
        "want", "wanna", "gonna", "gotta", "give", "make", "take", "keep", "let", "missed",
        "five", "twenty", "hundred", "thousand",
        "na", "ng", "sa", "ang", "mga", "at", "ay", "ko", "mo", "ni", "kay", "para", "kung", "kapag"
    )

    fun parse(file: File): MidiTimeline = parse(file.readBytes(), file.name)

    fun parse(bytes: ByteArray, sourceName: String = "MIDI"): MidiTimeline {
        require(bytes.size >= 14 && ascii(bytes, 0, 4) == "MThd") { "This is not a Standard MIDI file." }
        val headerLength = u32(bytes, 4).toInt()
        require(headerLength >= 6 && 8L + headerLength <= bytes.size) { "The MIDI header is invalid." }
        val tracks = u16(bytes, 10)
        val division = u16(bytes, 12)
        require(tracks > 0 && division != 0) { "The MIDI file has no tracks or timing division." }

        val cursor = Cursor(8 + headerLength)
        var finalTick = 0L
        val tempos = mutableListOf(Tempo(0, 500_000))
        val lyricEvents = mutableListOf<RawLyric>()
        val textEvents = mutableListOf<RawLyric>()
        val rawChannelEvents = ArrayList<RawChannelEvent>(4096)
        var eventOrder = 0
        var lyricOrder = 0

        repeat(tracks) { trackIdx ->
            if (cursor.position + 8 > bytes.size) return@repeat
            val chunkId = ascii(bytes, cursor.position, 4)
            val trackLength = u32(bytes, cursor.position + 4).toInt()
            cursor.position += 8
            if (trackLength < 0) return@repeat
            val trackEnd = (cursor.position.toLong() + trackLength).coerceAtMost(bytes.size.toLong()).toInt()
            if (chunkId != "MTrk") {
                cursor.position = trackEnd
                return@repeat
            }

            var tick = 0L
            var runningStatus = -1
            while (cursor.position < trackEnd) {
                val delta = readVlqSafe(bytes, cursor, trackEnd) ?: break
                tick += delta.toLong()
                finalTick = maxOf(finalTick, tick)
                if (cursor.position >= trackEnd) break

                var status = bytes[cursor.position].toInt() and 0xff
                if (status and 0x80 != 0) {
                    cursor.position++
                    if (status < 0xf0) runningStatus = status
                } else {
                    if (runningStatus < 0) break
                    status = runningStatus
                }

                when {
                    status == 0xff -> {
                        if (cursor.position >= trackEnd) break
                        val type = bytes[cursor.position++].toInt() and 0xff
                        val length = readVlqSafe(bytes, cursor, trackEnd) ?: break
                        val eventEnd = (cursor.position.toLong() + length).coerceAtMost(trackEnd.toLong()).toInt()
                        val actualLen = (eventEnd - cursor.position).coerceAtLeast(0)
                        when {
                            type == 0x51 && actualLen == 3 -> {
                                val micros = ((bytes[cursor.position].toInt() and 0xff) shl 16) or
                                    ((bytes[cursor.position + 1].toInt() and 0xff) shl 8) or
                                    (bytes[cursor.position + 2].toInt() and 0xff)
                                if (micros > 0) tempos += Tempo(tick, micros)
                            }
                            type == 0x05 && actualLen > 0 -> {
                                val decoded = decodeSmart(bytes, cursor.position, actualLen)
                                if (!isMetadataHeader(decoded, tick, isTextMeta = false)) {
                                    lyricEvents += RawLyric(
                                        trackIndex = trackIdx,
                                        order = lyricOrder++,
                                        tick = tick,
                                        rawText = decoded,
                                        metaType = 0x05,
                                        isKarMeta = false
                                    )
                                }
                            }
                            type == 0x01 && actualLen > 0 -> {
                                val decoded = decodeSmart(bytes, cursor.position, actualLen)
                                if (!isMetadataHeader(decoded, tick, isTextMeta = true)) {
                                    textEvents += RawLyric(
                                        trackIndex = trackIdx,
                                        order = lyricOrder++,
                                        tick = tick,
                                        rawText = decoded,
                                        metaType = 0x01,
                                        isKarMeta = decoded.trimStart().startsWith("@")
                                    )
                                }
                            }
                        }
                        cursor.position = eventEnd
                        if (type == 0x2f) cursor.position = trackEnd
                    }
                    status == 0xf0 || status == 0xf7 -> {
                        val length = readVlqSafe(bytes, cursor, trackEnd) ?: break
                        cursor.position = (cursor.position.toLong() + length).coerceAtMost(trackEnd.toLong()).toInt()
                    }
                    status in 0x80..0xef -> {
                        val kind = status and 0xf0
                        val channel = status and 0x0f
                        val count = if (kind == 0xc0 || kind == 0xd0) 1 else 2
                        if (cursor.position + count > trackEnd) break
                        val d1 = bytes[cursor.position].toInt() and 0x7f
                        val d2 = if (count == 2) bytes[cursor.position + 1].toInt() and 0x7f else 0
                        cursor.position += count

                        when (kind) {
                            0xb0 -> rawChannelEvents.add(
                                RawChannelEvent(tick, eventOrder++, MidiEventType.CONTROL_CHANGE, channel, d1, d2)
                            )
                            0xe0 -> rawChannelEvents.add(
                                RawChannelEvent(tick, eventOrder++, MidiEventType.PITCH_BEND, channel, d1, d2)
                            )
                            0xd0 -> rawChannelEvents.add(
                                RawChannelEvent(tick, eventOrder++, MidiEventType.CHANNEL_PRESSURE, channel, d1, 0)
                            )
                            0xa0 -> rawChannelEvents.add(
                                RawChannelEvent(tick, eventOrder++, MidiEventType.KEY_PRESSURE, channel, d1, d2)
                            )
                            0x90 -> {
                                if (d2 > 0) {
                                    rawChannelEvents.add(
                                        RawChannelEvent(tick, eventOrder++, MidiEventType.NOTE_ON, channel, d1, d2)
                                    )
                                } else {
                                    rawChannelEvents.add(
                                        RawChannelEvent(tick, eventOrder++, MidiEventType.NOTE_OFF, channel, d1, 0)
                                    )
                                }
                            }
                            0x80 -> {
                                rawChannelEvents.add(
                                    RawChannelEvent(tick, eventOrder++, MidiEventType.NOTE_OFF, channel, d1, d2)
                                )
                            }
                            0xc0 -> {
                                rawChannelEvents.add(
                                    RawChannelEvent(tick, eventOrder++, MidiEventType.PROGRAM_CHANGE, channel, d1, 0)
                                )
                            }
                        }
                    }
                    else -> {
                        // Unknown system common/realtime byte; skip safely
                    }
                }
            }
            cursor.position = trackEnd
        }

        val timing = buildTempoMap(tempos, division)
        val firstTempoMicros = tempos.firstOrNull { it.microsPerQuarter > 0 }?.microsPerQuarter ?: 500_000
        val bpm = (60_000_000L / firstTempoMicros).toInt().coerceIn(40, 240)

        // Sort channel events by tick and original encounter order
        rawChannelEvents.sortWith(compareBy<RawChannelEvent> { it.tick }.thenBy { it.order })

        val initialPrograms = IntArray(16) { 0 }
        val programSeen = BooleanArray(16)
        val bassChannels = mutableSetOf<Int>()
        val sequencerEvents = ArrayList<SequencerMidiEvent>(rawChannelEvents.size)

        for (ev in rawChannelEvents) {
            val timeUs = timing.toUs(ev.tick)
            val timeMs = timeUs / 1000L
            if (ev.type == MidiEventType.PROGRAM_CHANGE) {
                if (!programSeen[ev.channel] && timeMs <= 2000L) {
                    initialPrograms[ev.channel] = ev.data1
                    programSeen[ev.channel] = true
                }
                if (ev.data1 in 32..39 && ev.channel != 9) {
                    bassChannels.add(ev.channel)
                }
            }
            sequencerEvents.add(
                SequencerMidiEvent(
                    tick = ev.tick,
                    timeUs = timeUs,
                    timeMs = timeMs,
                    type = ev.type,
                    channel = ev.channel,
                    data1 = ev.data1,
                    data2 = ev.data2
                )
            )
        }

        val durationFromEventsMs = sequencerEvents.lastOrNull()?.timeMs ?: 0L
        val durationMs = maxOf(timing.toMs(finalTick), durationFromEventsMs)

        // Extract and reconstruct timed lyric events (preserving standalone line breaks & word boundaries)
        val timedLyrics = extractTimedLyricEvents(lyricEvents, textEvents, timing)

        val melodyChannel = detectMelodyChannel(sequencerEvents, timedLyrics, bassChannels)
        val melodyNotes = extractMelodyNotes(sequencerEvents, melodyChannel, durationMs)
        val rawLyricLines = buildLyricLines(timedLyrics, durationMs)
        val lyricLines = LyricsSyncEngine.refineSyllableEndingsWithMelodyNotes(rawLyricLines, melodyNotes, durationMs)
        val distinctTimestamps = timedLyrics.map { it.timeMs }.distinct().size
        val hasGenuineTiming = timedLyrics.size > 1 &&
            distinctTimestamps > 1 &&
            (timedLyrics.last().timeMs - timedLyrics.first().timeMs) >= 800L

        val timeline = MidiTimeline(
            durationMs = durationMs,
            lyrics = timedLyrics,
            hasLyrics = timedLyrics.isNotEmpty(),
            hasEventTiming = hasGenuineTiming,
            lyricLines = lyricLines,
            midiEvents = sequencerEvents,
            melodyNotes = melodyNotes,
            melodyChannel = melodyChannel,
            bassChannels = bassChannels,
            initialPrograms = initialPrograms,
            bpm = bpm
        )

        logLyricsDiagnosticsIfAvailable(sourceName, timeline)
        return timeline
    }

    /**
     * Reconstructs [LyricWord] units from a line's [LyricSyllable]s so that multi-syllable words
     * (e.g., `"kn" + "o" + "w,"` -> `"know,"`) stay grouped as a single visual word while preserving
     * each syllable's individual `[startMs, endMs]` timestamps.
     */
    fun reconstructWordsFromSyllables(lineText: String, syllables: List<LyricSyllable>): List<LyricWord> {
        if (syllables.isEmpty()) return emptyList()
        val words = ArrayList<LyricWord>(syllables.size)
        var currentGroup = ArrayList<LyricSyllable>(4)

        for (i in syllables.indices) {
            val syl = syllables[i]
            currentGroup.add(syl)
            val next = syllables.getOrNull(i + 1)
            val continuesIntoNext = next != null && (
                !syl.endsWord ||
                    (syl.charEnd > syl.charStart && next.charStart == syl.charEnd &&
                        (syl.charEnd >= lineText.length || !lineText[syl.charEnd].isWhitespace()))
                )
            if (!continuesIntoNext) {
                val first = currentGroup.first()
                val last = currentGroup.last()
                val wordText = currentGroup.joinToString("") { it.text }
                words.add(
                    LyricWord(
                        text = wordText,
                        startMs = first.startMs,
                        endMs = last.endMs,
                        charStart = first.charStart,
                        charEnd = last.charEnd,
                        syllables = currentGroup.toList()
                    )
                )
                currentGroup = ArrayList(4)
            }
        }
        return words
    }

    /**
     * Selects the authoritative lyric track (preventing duplicate interleaved tracks when multiple
     * tracks contain lyrics), preserves standalone `\r` / `\n` / `/` / `\` line break events,
     * cleans KAR control sequences (`^`, `%+`, `~`, `+`, `` ` ``), and determines accurate
     * syllable-vs-word boundaries (`endsWord`).
     */
    private fun extractTimedLyricEvents(
        lyricEvents: List<RawLyric>,
        textEvents: List<RawLyric>,
        timing: Timing
    ): List<TimedLyricEvent> {
        val hasVisibleLyrics = lyricEvents.any { extractVisibleCore(it.rawText).isNotEmpty() }
        val pool = (if (hasVisibleLyrics) lyricEvents else textEvents)
            .filter { !it.isKarMeta }

        if (pool.isEmpty()) return emptyList()

        // Group by track to detect multi-track duplicate lyrics (e.g. Track 1 syllables + Track 29 words)
        val byTrack = pool.groupBy { it.trackIndex }
            .filterValues { list -> list.any { extractVisibleCore(it.rawText).isNotEmpty() } }

        if (byTrack.isEmpty()) return emptyList()

        val selectedRawEvents: List<RawLyric>
        var secondarySpaceReference: List<RawLyric>? = null

        if (byTrack.size == 1) {
            selectedRawEvents = byTrack.values.first().sortedWith(compareBy<RawLyric> { it.tick }.thenBy { it.order })
        } else {
            val trackEntries = byTrack.entries.map { (trackIdx, events) ->
                val sorted = events.sortedWith(compareBy<RawLyric> { it.tick }.thenBy { it.order })
                val visibleEvents = sorted.filter { extractVisibleCore(it.rawText).isNotEmpty() }
                val normText = visibleEvents.joinToString("") {
                    extractVisibleCore(it.rawText).lowercase().filter { ch -> ch.isLetterOrDigit() }
                }
                val spaceCount = sorted.count { it.rawText.contains(' ') || it.rawText.contains('\t') }
                val spaceRatio = if (visibleEvents.isNotEmpty()) spaceCount.toDouble() / visibleEvents.size else 0.0
                TrackLyricSummary(
                    trackIndex = trackIdx,
                    events = sorted,
                    visibleCount = visibleEvents.size,
                    normalizedAlnum = normText,
                    spaceRatio = spaceRatio,
                    firstTick = visibleEvents.firstOrNull()?.tick ?: 0L,
                    lastTick = visibleEvents.lastOrNull()?.tick ?: 0L
                )
            }.sortedByDescending { it.visibleCount }

            val primary = trackEntries.first()
            val others = trackEntries.drop(1)

            // Check if other tracks are duplicate versions of the same lyrics or separate song sections
            val nonDuplicateTracks = mutableListOf(primary)
            for (other in others) {
                if (other.visibleCount < 3) continue
                val isOverlappingTime = other.firstTick <= primary.lastTick && other.lastTick >= primary.firstTick
                val isSimilarText = isDuplicateLyricText(primary.normalizedAlnum, other.normalizedAlnum)
                if (isOverlappingTime && isSimilarText) {
                    // Duplicate lyric track! If primary lacks word spaces and this track has word spaces, save it as reference
                    if (primary.spaceRatio < 0.12 && other.spaceRatio >= 0.35 && secondarySpaceReference == null) {
                        secondarySpaceReference = other.events
                    }
                } else if (!isOverlappingTime) {
                    nonDuplicateTracks.add(other)
                }
            }

            selectedRawEvents = nonDuplicateTracks
                .flatMap { it.events }
                .sortedWith(compareBy<RawLyric> { it.tick }.thenBy { it.order })
        }

        val trackHasAlmostNoExplicitSpaces = selectedRawEvents.count {
            it.rawText.contains(' ') || it.rawText.contains('\t')
        } <= (selectedRawEvents.size * 0.08).toInt()

        val normalizedTokens = tokenizeRawLyrics(selectedRawEvents, timing)
        if (normalizedTokens.isEmpty()) return emptyList()

        val timedLyrics = ArrayList<TimedLyricEvent>(normalizedTokens.size)
        for (i in normalizedTokens.indices) {
            val cur = normalizedTokens[i]
            val prev = normalizedTokens.getOrNull(i - 1)
            val next = normalizedTokens.getOrNull(i + 1)

            val pauseFromPrevMs = if (prev != null) cur.timeMs - prev.timeMs else 0L
            val startsParagraph = i == 0 ||
                cur.leadingParagraphBreak ||
                (prev != null && prev.trailingParagraphBreak) ||
                pauseFromPrevMs > 4200L

            val startsLine = startsParagraph ||
                cur.leadingLineBreak ||
                (prev != null && prev.trailingLineBreak) ||
                (pauseFromPrevMs >= 2400L && (prev == null || prev.hasTrailingSpace || cur.hasLeadingSpace))

            val endsWord = when {
                next == null -> true
                cur.trailingLineBreak || cur.trailingParagraphBreak -> true
                next.leadingLineBreak || next.leadingParagraphBreak -> true
                cur.hasTrailingSpace || next.hasLeadingSpace -> true
                cur.isHyphenPrefix -> false
                next.timeMs - cur.timeMs >= 2200L -> true
                endsWithWordTerminalPunctuation(cur.text) &&
                    (next.text.firstOrNull()?.isLetter() == true || next.text.firstOrNull() == '(') -> true
                trackHasAlmostNoExplicitSpaces && inferZeroSpaceWordBoundary(cur.text, next.text) -> true
                else -> false
            }

            timedLyrics.add(
                TimedLyricEvent(
                    timeMs = cur.timeMs,
                    text = cur.text,
                    beginsLine = startsLine,
                    endsWord = endsWord,
                    beginsParagraph = startsParagraph
                )
            )
        }

        return timedLyrics
    }

    private data class TrackLyricSummary(
        val trackIndex: Int,
        val events: List<RawLyric>,
        val visibleCount: Int,
        val normalizedAlnum: String,
        val spaceRatio: Double,
        val firstTick: Long,
        val lastTick: Long
    )

    private fun isDuplicateLyricText(primaryAlnum: String, otherAlnum: String): Boolean {
        if (primaryAlnum.isEmpty() || otherAlnum.isEmpty()) return false
        val sampleLen = minOf(80, primaryAlnum.length, otherAlnum.length)
        if (sampleLen < 12) return primaryAlnum == otherAlnum
        val prefixA = primaryAlnum.take(sampleLen)
        val prefixB = otherAlnum.take(sampleLen)
        if (prefixA.take(24) == prefixB.take(24)) return true
        if (primaryAlnum.contains(prefixB.take(24)) || otherAlnum.contains(prefixA.take(24))) return true
        return false
    }

    private val knownJoinedWordsInZeroSpaceTracks = setOf(
        "now", "after", "being", "untrue", "river", "over", "sorry", "unkind",
        "yeah", "before", "away", "again", "around", "without", "inside", "outside",
        "maybe", "nothing", "something", "everything", "anything", "someone", "anyone",
        "forever", "together", "another", "into", "onto", "upon", "within"
    )

    private val standaloneWordsInZeroSpaceTracks = setOf(
        "i", "you", "he", "she", "we", "they", "it", "me", "him", "her", "us", "them",
        "my", "your", "his", "our", "their", "mine", "yours",
        "to", "of", "in", "on", "at", "for", "with", "from", "by", "as", "if", "or", "and", "but",
        "that", "this", "what", "why", "how", "who", "when", "where",
        "you're", "don't", "can't", "won't", "i'm", "i've", "i'll", "it's", "that's",
        "want", "cry", "cried", "say", "love", "right", "all", "so", "oh", "yeah"
    )

    private fun inferZeroSpaceWordBoundary(curText: String, nextText: String): Boolean {
        if (curText.isEmpty() || nextText.isEmpty()) return false
        val curClean = curText.trim('(', ')', ',', '.', '!', '?', '"').lowercase()
        val nextClean = nextText.trim('(', ')', ',', '.', '!', '?', '"').lowercase()
        if (curClean.isEmpty() || nextClean.isEmpty()) return false

        // A single consonant letter can never start a new standalone word
        val allowedSingleLetterWords = setOf("a", "i", "o", "e")
        if (nextClean.length == 1 && nextClean !in allowedSingleLetterWords) return false

        if (nextText.startsWith('(') || curText.endsWith(')')) return true

        // Transition from a word to a capitalized word (e.g., "Oh" + "Cry" or "I" + "want")
        val curFirst = curText.first()
        val nextFirst = nextText.first()
        if (nextFirst.isUpperCase() && curText.any { it.isLowerCase() }) return true
        if (curText == "I" && nextFirst.isLetter()) return true

        // Contractions with apostrophe (e.g. you' + re or We + 're) must not be split
        if (curText.endsWith('\'') || nextText.startsWith('\'')) return false

        val combined = curClean + nextClean
        if (combined in knownJoinedWordsInZeroSpaceTracks) return false

        // Common suffixes should not be split from their root word
        if (nextClean in setOf("ing", "ed", "er", "est", "ly", "ness", "tion", "sion", "ry", "ty", "ful", "less", "ment", "able", "ible")) return false

        if (nextClean in standaloneWordsInZeroSpaceTracks) return true
        if (curClean in standaloneWordsInZeroSpaceTracks) return true
        return false
    }

    /**
     * Converts raw MIDI/KAR lyric events into normalized syllable tokens while preserving
     * standalone line breaks (`\r`, `\n`, `/`, `\`), standalone word spaces (`" "`),
     * and compound word hyphens (`~`, `+`).
     */
    private fun tokenizeRawLyrics(
        rawEvents: List<RawLyric>,
        timing: Timing
    ): List<NormalizedToken> {
        val tokens = ArrayList<NormalizedToken>(rawEvents.size)
        var pendingLineBreak = false
        var pendingParagraphBreak = false
        var pendingLeadingSpace = false

        for (rawEv in rawEvents) {
            val raw = rawEv.rawText
            if (raw.isEmpty()) continue

            // Check if the event contains internal line breaks splitting multiple text segments
            val segments = splitPreservingLineBreaks(raw)
            for (seg in segments) {
                val hasParagraphBreak = seg.leadingParagraph || seg.trailingParagraph
                val hasLineBreak = hasParagraphBreak || seg.leadingBreak || seg.trailingBreak

                val strippedControls = stripKarControlSequences(seg.body)
                val hasLeadSpace = strippedControls.firstOrNull()?.let { it == ' ' || it == '\t' } == true
                val hasTrailSpace = strippedControls.lastOrNull()?.let { it == ' ' || it == '\t' } == true

                val trimmed = strippedControls.trim()
                if (trimmed.isEmpty()) {
                    // Standalone line break or standalone space event
                    if (seg.leadingParagraph || seg.trailingParagraph) {
                        if (tokens.isNotEmpty()) {
                            val lastIdx = tokens.lastIndex
                            tokens[lastIdx] = tokens[lastIdx].copy(
                                trailingLineBreak = true,
                                trailingParagraphBreak = true,
                                hasTrailingSpace = true
                            )
                        } else {
                            pendingParagraphBreak = true
                            pendingLineBreak = true
                        }
                    } else if (seg.leadingBreak || seg.trailingBreak) {
                        if (tokens.isNotEmpty()) {
                            val lastIdx = tokens.lastIndex
                            tokens[lastIdx] = tokens[lastIdx].copy(
                                trailingLineBreak = true,
                                hasTrailingSpace = true
                            )
                        } else {
                            pendingLineBreak = true
                        }
                    } else if (hasLeadSpace || hasTrailSpace) {
                        if (tokens.isNotEmpty()) {
                            val lastIdx = tokens.lastIndex
                            tokens[lastIdx] = tokens[lastIdx].copy(hasTrailingSpace = true)
                        } else {
                            pendingLeadingSpace = true
                        }
                    }
                    continue
                }

                val normalized = normalizeSyllableCore(trimmed)
                if (normalized.text.isEmpty()) {
                    if (hasLineBreak && tokens.isNotEmpty()) {
                        val lastIdx = tokens.lastIndex
                        tokens[lastIdx] = tokens[lastIdx].copy(
                            trailingLineBreak = true,
                            trailingParagraphBreak = tokens[lastIdx].trailingParagraphBreak || hasParagraphBreak,
                            hasTrailingSpace = true
                        )
                    } else if (hasLeadSpace || hasTrailSpace) {
                        if (tokens.isNotEmpty()) {
                            val lastIdx = tokens.lastIndex
                            tokens[lastIdx] = tokens[lastIdx].copy(hasTrailingSpace = true)
                        }
                    }
                    continue
                }

                // If this syllable starts with a hyphen (e.g. "-lo"), attach it to the previous syllable
                if (normalized.startsWithSoftHyphen && tokens.isNotEmpty() && !pendingLineBreak && !seg.leadingBreak) {
                    val lastIdx = tokens.lastIndex
                    tokens[lastIdx] = tokens[lastIdx].copy(hasTrailingSpace = false)
                }

                val timeMs = timing.toMs(rawEv.tick)
                tokens.add(
                    NormalizedToken(
                        tick = rawEv.tick,
                        timeMs = timeMs,
                        text = normalized.text,
                        hasLeadingSpace = pendingLeadingSpace || hasLeadSpace,
                        hasTrailingSpace = hasTrailSpace && !normalized.isCompoundHyphenPrefix,
                        leadingLineBreak = pendingLineBreak || seg.leadingBreak || seg.leadingParagraph,
                        trailingLineBreak = seg.trailingBreak || seg.trailingParagraph,
                        leadingParagraphBreak = pendingParagraphBreak || seg.leadingParagraph,
                        trailingParagraphBreak = seg.trailingParagraph,
                        isHyphenPrefix = normalized.isCompoundHyphenPrefix || normalized.endsWithSoftHyphen
                    )
                )

                pendingLineBreak = false
                pendingParagraphBreak = false
                pendingLeadingSpace = false
            }
        }

        return tokens
    }

    private data class RawSegment(
        val body: String,
        val leadingBreak: Boolean,
        val trailingBreak: Boolean,
        val leadingParagraph: Boolean,
        val trailingParagraph: Boolean
    )

    /**
     * Splits a raw meta-event string if it contains internal line-break characters (`\r`, `\n`, `/`, `\`),
     * recording whether each piece had leading/trailing line or paragraph breaks.
     */
    private fun splitPreservingLineBreaks(raw: String): List<RawSegment> {
        var idx = 0
        val n = raw.length

        fun consumeBreaks(): Pair<Boolean, Boolean> {
            var lineBrk = false
            var paraBrk = false
            var newlineCount = 0
            while (idx < n) {
                val c = raw[idx]
                when {
                    c == '\\' -> {
                        lineBrk = true
                        paraBrk = true
                        idx++
                    }
                    c == '/' -> {
                        lineBrk = true
                        idx++
                    }
                    c == '\r' -> {
                        lineBrk = true
                        newlineCount++
                        idx++
                        if (idx < n && raw[idx] == '\n') idx++
                    }
                    c == '\n' -> {
                        lineBrk = true
                        newlineCount++
                        idx++
                    }
                    else -> break
                }
            }
            if (newlineCount >= 2) paraBrk = true
            return lineBrk to paraBrk
        }

        val out = ArrayList<RawSegment>(2)
        val (firstLeadLine, firstLeadPara) = consumeBreaks()
        if (idx >= n) {
            out.add(RawSegment("", firstLeadLine, firstLeadLine, firstLeadPara, firstLeadPara))
            return out
        }

        var leadLine = firstLeadLine
        var leadPara = firstLeadPara
        while (idx < n) {
            val start = idx
            while (idx < n && raw[idx] != '\r' && raw[idx] != '\n' && raw[idx] != '/' && raw[idx] != '\\') {
                idx++
            }
            val body = raw.substring(start, idx)
            val (trailLine, trailPara) = consumeBreaks()
            out.add(RawSegment(body, leadLine, trailLine, leadPara, trailPara))
            leadLine = trailLine
            leadPara = trailPara
        }
        return out
    }

    private data class NormalizedCoreResult(
        val text: String,
        val isCompoundHyphenPrefix: Boolean,
        val startsWithSoftHyphen: Boolean,
        val endsWithSoftHyphen: Boolean
    )

    private fun stripKarControlSequences(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                // Strip "%+" KAR control sequence
                ch == '%' && i + 1 < input.length && input[i + 1] == '+' -> {
                    i += 2
                }
                // Strip '^' KAR control marker
                ch == '^' -> {
                    i++
                }
                // Strip ASCII non-printable control chars except tab/space
                ch.code in 0..31 || ch.code == 127 -> {
                    if (ch == '\t') sb.append(' ')
                    i++
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun normalizeSyllableCore(trimmed: String): NormalizedCoreResult {
        var s = trimmed
        // Standalone melisma markers (~ or +) are not displayable words
        if (s.all { it == '~' || it == '+' || it == '_' }) {
            return NormalizedCoreResult("", false, false, false)
        }

        // Convert leading backtick before letters (`YONG, `cause, `til) and internal backtick (don`t) to apostrophe
        if (s.length >= 2 && s.startsWith('`') && s[1].isLetter()) {
            s = "'" + s.substring(1)
        }
        s = s.replace(Regex("(?<=\\p{L})`(?=\\p{L})"), "'")

        // Convert Spanish KAR acute accent sequences (<letter>'<vowel>, e.g. S'E -> SÉ, CORAZ'ON -> CORAZÓN)
        s = replaceSpanishKarAccents(s)

        // Convert KAR compound hyphen markers (~ or + after letters, e.g., "PAG~" -> "PAG-", "DIS+" -> "DIS-")
        var isCompoundHyphenPrefix = false
        if (s.length >= 2 && (s.endsWith('~') || s.endsWith('+')) && s[s.length - 2].isLetter()) {
            s = s.dropLast(1) + "-"
            isCompoundHyphenPrefix = true
        }
        // Internal ~ or + between letters ("PAG~IBIG") -> "-"
        s = s.replace(Regex("(?<=\\p{L})[~+](?=\\p{L})"), "-")
        // Strip any remaining leading/trailing ~ or +
        s = s.trim('~', '+')

        // Check for soft syllable-joining hyphens ("-lo" or "Hel-")
        val startsWithSoftHyphen = s.length >= 2 && s.startsWith('-') && s[1].isLetter()
        val endsWithSoftHyphen = !isCompoundHyphenPrefix && s.length >= 2 && s.endsWith('-') && s[s.length - 2].isLetter()

        if (startsWithSoftHyphen) {
            s = s.removePrefix("-")
        }
        if (endsWithSoftHyphen) {
            s = s.removeSuffix("-")
        }

        s = s.replace(Regex("\\s+"), " ").trim()
        return NormalizedCoreResult(
            text = s,
            isCompoundHyphenPrefix = isCompoundHyphenPrefix,
            startsWithSoftHyphen = startsWithSoftHyphen,
            endsWithSoftHyphen = endsWithSoftHyphen
        )
    }

    private fun extractVisibleCore(raw: String): String {
        val noBreaks = raw.replace(Regex("[\\r\\n/\\\\]+"), " ")
        val noControls = stripKarControlSequences(noBreaks).trim()
        return normalizeSyllableCore(noControls).text
    }

    private fun endsWithWordTerminalPunctuation(text: String): Boolean {
        val last = text.lastOrNull() ?: return false
        return last == ',' || last == ';' || last == ':' || last == '!' || last == '?' || last == ')' || last == ']'
    }

    /**
     * Reconstructs natural lyric lines from [TimedLyricEvent]s:
     * 1. Groups syllables into initial phrases using KAR/MIDI line breaks (`beginsLine`).
     * 2. Merges overly short 16-char hardware LCD sub-phrases into natural phrases when the
     *    inter-phrase musical gap is short and the combined length fits comfortably on screen.
     * 3. Wraps overly long lines (> 42 chars) ONLY at word boundaries (`endsWord == true`),
     *    never splitting inside a word.
     * 4. Concatenates syllables into `LyricLine.text` inserting spaces ONLY between words (`endsWord == true`).
     */
    internal fun buildLyricLines(events: List<TimedLyricEvent>, durationMs: Long): List<LyricLine> {
        if (events.isEmpty()) return emptyList()

        // Step 1: Group into initial phrase segments based on KAR/MIDI line breaks, never splitting inside a word
        val phraseSegments = mutableListOf<MutableList<TimedLyricEvent>>()
        var currentSegment = mutableListOf<TimedLyricEvent>()

        for (i in events.indices) {
            val ev = events[i]
            val prev = currentSegment.lastOrNull()
            val canBreakHere = prev == null || prev.endsWord
            if (currentSegment.isNotEmpty() && ev.beginsLine && canBreakHere) {
                phraseSegments.add(currentSegment)
                currentSegment = mutableListOf()
            }
            currentSegment.add(ev)
        }
        if (currentSegment.isNotEmpty()) phraseSegments.add(currentSegment)

        // Step 2: Merge short 16-char hardware LCD sub-phrases into natural karaoke lines
        val mergedSegments = mergeShortHardwareLcdSubPhrases(phraseSegments)

        // Step 3: Wrap any overly long segments (> 42 chars) strictly at word boundaries
        val finalGroups = mutableListOf<List<TimedLyricEvent>>()
        for (segment in mergedSegments) {
            splitLongSegmentAtWordBoundaries(segment, maxLineChars = 42, out = finalGroups)
        }

        // Step 4: Construct LyricLine, LyricSyllable, and LyricWord instances with exact character ranges
        return finalGroups.mapIndexed { lineIdx, group ->
            val nextLineStart = finalGroups.getOrNull(lineIdx + 1)?.firstOrNull()?.timeMs ?: durationMs
            val sb = StringBuilder()
            val syllables = ArrayList<LyricSyllable>(group.size)

            for (i in group.indices) {
                val item = group[i]
                val nextSyllableStart = group.getOrNull(i + 1)?.timeMs
                    ?: minOf(nextLineStart, item.timeMs + 1800L)
                val endMs = maxOf(item.timeMs + 120L, minOf(nextSyllableStart, item.timeMs + 2500L))

                val prevItem = group.getOrNull(i - 1)
                if (sb.isNotEmpty() && prevItem != null && prevItem.endsWord && !sb.endsWith("-")) {
                    sb.append(' ')
                }

                val charStart = sb.length
                val displayToken = item.text
                sb.append(displayToken)
                val charEnd = sb.length
                val isLastInLine = i == group.lastIndex
                val effectiveEndsWord = if (isLastInLine) true else item.endsWord

                syllables.add(
                    LyricSyllable(
                        text = displayToken,
                        startMs = item.timeMs,
                        endMs = endMs,
                        charStart = charStart,
                        charEnd = charEnd,
                        endsWord = effectiveEndsWord
                    )
                )
            }

            val lineText = sb.toString()
            val lineStart = syllables.first().startMs
            val lineEnd = maxOf(syllables.last().endMs, minOf(nextLineStart, syllables.last().startMs + 2000L))
            val words = reconstructWordsFromSyllables(lineText, syllables)

            LyricLine(
                index = lineIdx,
                startMs = lineStart,
                endMs = lineEnd,
                text = lineText,
                syllables = syllables,
                words = words
            )
        }
    }

    private fun computeSegmentCharLength(seg: List<TimedLyricEvent>): Int {
        var len = 0
        for (i in seg.indices) {
            len += seg[i].text.length
            if (i < seg.lastIndex && seg[i].endsWord && !seg[i].text.endsWith("-")) {
                len += 1
            }
        }
        return len
    }

    private fun lastWordLowercase(seg: List<TimedLyricEvent>): String {
        val sb = StringBuilder()
        for (i in seg.indices.reversed()) {
            sb.insert(0, seg[i].text)
            if (i > 0 && seg[i - 1].endsWord) break
        }
        return sb.toString().trim().lowercase()
    }

    private fun firstWordLowercase(seg: List<TimedLyricEvent>): String {
        val sb = StringBuilder()
        for (i in seg.indices) {
            sb.append(seg[i].text)
            if (seg[i].endsWord) break
        }
        return sb.toString().trim().lowercase()
    }

    private fun replaceSpanishKarAccents(input: String): String {
        if (!input.contains('\'') || input.length < 3) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (i > 0 && input[i] == '\'' && input[i - 1].isLetter() && i + 1 < input.length) {
                val accented = when (input[i + 1]) {
                    'A' -> 'Á'
                    'E' -> 'É'
                    'I' -> 'Í'
                    'O' -> 'Ó'
                    'U' -> 'Ú'
                    'a' -> 'á'
                    'e' -> 'é'
                    'i' -> 'í'
                    'o' -> 'ó'
                    'u' -> 'ú'
                    else -> null
                }
                if (accented != null) {
                    sb.append(accented)
                    i += 2
                    continue
                }
            }
            sb.append(input[i])
            i++
        }
        return sb.toString()
    }

    /**
     * Merges short hardware LCD sub-phrases (typically 6–22 chars separated by `\r` or `/` every 1–3 words)
     * into complete, natural karaoke musical phrases across all songs (case-insensitive: handles ALL-CAPS,
     * mixed-case, and lowercase lyrics) when the musical gap is within natural phrase boundaries.
     */
    private fun mergeShortHardwareLcdSubPhrases(
        segments: List<MutableList<TimedLyricEvent>>
    ): List<List<TimedLyricEvent>> {
        if (segments.size <= 1) return segments

        val out = ArrayList<List<TimedLyricEvent>>(segments.size)
        var i = 0
        while (i < segments.size) {
            val current = ArrayList<TimedLyricEvent>(segments[i])
            while (i + 1 < segments.size) {
                val next = segments[i + 1]
                if (!shouldMergeLcdSubPhrases(current, next, segments.getOrNull(i + 2))) {
                    break
                }
                current.addAll(next)
                i++
            }
            out.add(current)
            i++
        }
        return out
    }

    private fun shouldMergeLcdSubPhrases(
        segA: List<TimedLyricEvent>,
        segB: List<TimedLyricEvent>,
        segC: List<TimedLyricEvent>?
    ): Boolean {
        if (segA.isEmpty() || segB.isEmpty()) return false
        if (segB.first().beginsParagraph) return false

        val lenA = computeSegmentCharLength(segA)
        val lenB = computeSegmentCharLength(segB)
        val combinedAB = lenA + 1 + lenB
        // Keep combined phrases within clean screen readability (<= 42 chars)
        if (lenA > 26 || combinedAB > 42) return false

        val lastTextA = segA.last().text.trim()
        val lastCharA = lastTextA.lastOrNull() ?: ' '
        // Strong terminal punctuation indicates intentional musical/sentence ending
        if (lastCharA == '.' || lastCharA == '!' || lastCharA == '?' || lastCharA == '"') return false

        val firstWordB = firstWordLowercase(segB).trimEnd(',', ';', ':', '.', '!', '?')
        val lastWordA = lastWordLowercase(segA).trimEnd(',', ';', ':', '.', '!', '?')

        val gapAB = segB.first().timeMs - segA.last().timeMs
        if (gapAB < 0L || gapAB > 2500L) return false

        val firstCharB = segB.first().text.firstOrNull { it.isLetter() } ?: 'A'
        val isPronounI = firstWordB == "i" || firstWordB.startsWith("i'")
        val bStartsAsContinuation = firstCharB.isLowerCase() || (isPronounI && lastWordA in continuationTailWords)
        val grammaticallyContinues = lastWordA in continuationTailWords ||
            firstWordB in setOf("and", "or", "but", "to", "that", "it", "up", "in", "on", "of", "for", "with", "minutes", "too", "so", "then", "because", "ang", "ng", "sa", "mga", "ko", "mo", "na", "pa")

        val isContinuation = bStartsAsContinuation ||
            grammaticallyContinues ||
            lastCharA == '-' ||
            lastCharA == ',' ||
            lastCharA == ';'
        if (!isContinuation) return false

        // Lookahead: if segA + segB + segC cannot all fit together, check whether segB + segC is a tighter pair
        if (segC != null && segC.isNotEmpty() && !segC.first().beginsParagraph) {
            val lenC = computeSegmentCharLength(segC)
            if (combinedAB + 1 + lenC > 42 && lenB + 1 + lenC <= 42) {
                val gapBC = segC.first().timeMs - segB.last().timeMs
                val lastWordB = lastWordLowercase(segB).trimEnd(',', ';', ':', '.', '!', '?')
                val bContinuesIntoC = lastWordB in continuationTailWords
                if (gapBC in 0L..1200L && bContinuesIntoC && (gapBC + 120L < gapAB || lastWordA !in continuationTailWords)) {
                    return false
                }
            }
        }

        return when {
            // Hyphenated word or comma continuation: merge up to 2.2 seconds
            (lastCharA == '-' || lastCharA == ',' || lastCharA == ';') && gapAB <= 2200L -> true
            // Syntactic/grammatical continuation: merge up to 2.2 seconds
            (bStartsAsContinuation || grammaticallyContinues) && gapAB <= 2200L -> true
            else -> false
        }
    }

    /**
     * Splits a single phrase exceeding [maxLineChars] strictly at word boundaries (`endsWord == true`),
     * choosing balanced split points with the largest musical inter-word gap.
     */
    private fun splitLongSegmentAtWordBoundaries(
        segment: List<TimedLyricEvent>,
        maxLineChars: Int,
        out: MutableList<List<TimedLyricEvent>>
    ) {
        val totalLen = computeSegmentCharLength(segment)
        if (totalLen <= maxLineChars || segment.size <= 1) {
            out.add(segment)
            return
        }

        // Find all word boundary indices
        val wordEndIndices = segment.indices.filter { idx -> idx < segment.lastIndex && segment[idx].endsWord }
        if (wordEndIndices.isEmpty()) {
            out.add(segment)
            return
        }

        // Choose a split point that keeps the first part <= maxLineChars and balances both halves
        val targetLen = totalLen / 2
        var bestSplitIdx = -1
        var bestScore = Double.NEGATIVE_INFINITY

        var runningLen = 0
        for (idx in segment.indices) {
            runningLen += segment[idx].text.length
            if (idx < segment.lastIndex && segment[idx].endsWord) {
                if (runningLen in 10..maxLineChars) {
                    val gapMs = (segment[idx + 1].timeMs - segment[idx].timeMs).coerceIn(0L, 2000L)
                    val balancePenalty = abs(runningLen - targetLen) * 28.0
                    val score = gapMs.toDouble() - balancePenalty
                    if (score > bestScore) {
                        bestScore = score
                        bestSplitIdx = idx
                    }
                }
                if (!segment[idx].text.endsWith("-")) runningLen += 1
            }
        }

        if (bestSplitIdx < 0) {
            bestSplitIdx = wordEndIndices.firstOrNull { idx ->
                computeSegmentCharLength(segment.subList(0, idx + 1)) <= maxLineChars
            } ?: wordEndIndices.first()
        }

        val left = segment.subList(0, bestSplitIdx + 1)
        val right = segment.subList(bestSplitIdx + 1, segment.size)
        out.add(left)
        splitLongSegmentAtWordBoundaries(right, maxLineChars, out)
    }

    /**
     * Produces a human-readable diagnostic report of raw lyric meta-events, reconstructed words,
     * syllable timings, and final rendered lines for debugging and verification.
     */
    fun buildDiagnosticsReport(file: File): String =
        buildDiagnosticsReport(file.readBytes(), file.name)

    fun buildDiagnosticsReport(bytes: ByteArray, sourceName: String = "MIDI"): String {
        val timeline = parse(bytes, sourceName)
        val sb = StringBuilder(4096)
        sb.appendLine("=== JKTV LYRICS DIAGNOSTIC REPORT: $sourceName ===")
        sb.appendLine("Duration: ${timeline.durationMs} ms | BPM: ${timeline.bpm} | Melody Ch: ${timeline.melodyChannel}")
        sb.appendLine("Timed Syllables: ${timeline.lyrics.size} | Reconstructed Lines: ${timeline.lyricLines.size}")
        sb.appendLine()
        for (line in timeline.lyricLines) {
            val wordsDesc = line.words.joinToString(" | ") { w ->
                val sylsDesc = w.syllables.joinToString("+") { "${it.text}@${it.startMs}" }
                "\"${w.text}\"[$sylsDesc]"
            }
            sb.appendLine(
                String.format(
                    "Line %02d [%6d..%6d ms]: %-42s  --> Words(%d): %s",
                    line.index + 1,
                    line.startMs,
                    line.endMs,
                    "\"${line.text}\"",
                    line.words.size,
                    wordsDesc
                )
            )
        }
        return sb.toString()
    }

    private fun logLyricsDiagnosticsIfAvailable(sourceName: String, timeline: MidiTimeline) {
        runCatching {
            val logClass = Class.forName("android.util.Log")
            val dMethod = logClass.getMethod("d", String::class.java, String::class.java)
            dMethod.invoke(
                null,
                "JKTVLyrics",
                "Parsed $sourceName: ${timeline.lyrics.size} syllables -> ${timeline.lyricLines.size} lines"
            )
            for (line in timeline.lyricLines.take(8)) {
                val wordsSummary = line.words.joinToString(" ") { w ->
                    if (w.syllables.size > 1) {
                        "${w.text}(${w.syllables.joinToString("·") { it.text }})"
                    } else {
                        w.text
                    }
                }
                dMethod.invoke(
                    null,
                    "JKTVLyrics",
                    "L${line.index + 1} [${line.startMs}..${line.endMs}ms]: \"${line.text}\" | Words: $wordsSummary"
                )
            }
        }
    }

    /**
     * Correlates MIDI NoteOn timestamps with lyric syllable timestamps across channels 0..15 (excluding drums on 9).
     * Falls back to standard KAR melody channels (Channel 3 [MIDI Ch 4] or Channel 0 [MIDI Ch 1]).
     */
    private fun detectMelodyChannel(
        events: List<SequencerMidiEvent>,
        lyrics: List<TimedLyricEvent>,
        bassChannels: Set<Int>
    ): Int {
        val noteCounts = IntArray(16)
        val noteTimesByChannel = Array(16) { LongArrayList() }
        for (ev in events) {
            if (ev.type == MidiEventType.NOTE_ON && ev.channel != 9) {
                noteCounts[ev.channel]++
                noteTimesByChannel[ev.channel].add(ev.timeMs)
            }
        }

        if (lyrics.size >= 8) {
            var bestChannel = -1
            var bestScore = -1.0
            for (ch in 0..15) {
                if (ch == 9 || ch in bassChannels || noteCounts[ch] < lyrics.size / 3) continue
                val notes = noteTimesByChannel[ch]
                var matches = 0
                for (lyric in lyrics) {
                    if (notes.hasValueNear(lyric.timeMs, 140L)) {
                        matches++
                    }
                }
                val matchRatio = matches.toDouble() / lyrics.size.toDouble()
                val countCloseness = 1.0 / (1.0 + abs(noteCounts[ch] - lyrics.size).toDouble() / lyrics.size.coerceAtLeast(1))
                val channelBonus = when (ch) {
                    3 -> 0.18 // Standard KAR vocal melody channel (Channel 4)
                    0 -> 0.10 // Standard Type-1 vocal melody channel (Channel 1)
                    1 -> 0.05
                    else -> 0.0
                }
                val score = matchRatio * 0.70 + countCloseness * 0.30 + channelBonus
                if (score > bestScore) {
                    bestScore = score
                    bestChannel = ch
                }
            }
            if (bestChannel >= 0) return bestChannel
        }

        if (noteCounts[3] > 20) return 3
        if (noteCounts[0] > 20) return 0
        return (0..15).filter { it != 9 && it !in bassChannels }.maxByOrNull { noteCounts[it] } ?: 0
    }

    private fun extractMelodyNotes(
        events: List<SequencerMidiEvent>,
        melodyChannel: Int,
        durationMs: Long
    ): List<MelodyNote> {
        val notes = ArrayList<MelodyNote>(512)
        val activeStartMs = LongArray(128) { -1L }
        val activeVel = IntArray(128)
        for (ev in events) {
            if (ev.channel != melodyChannel) continue
            val key = ev.data1.coerceIn(0, 127)
            when (ev.type) {
                MidiEventType.NOTE_ON -> {
                    if (activeStartMs[key] >= 0L && ev.timeMs > activeStartMs[key]) {
                        notes.add(MelodyNote(activeStartMs[key], ev.timeMs, key, activeVel[key]))
                    }
                    activeStartMs[key] = ev.timeMs
                    activeVel[key] = ev.data2
                }
                MidiEventType.NOTE_OFF -> {
                    val start = activeStartMs[key]
                    if (start >= 0L && ev.timeMs > start) {
                        notes.add(MelodyNote(start, ev.timeMs, key, activeVel[key]))
                    }
                    activeStartMs[key] = -1L
                }
                else -> Unit
            }
        }
        for (key in 0..127) {
            val start = activeStartMs[key]
            if (start >= 0L && durationMs > start) {
                notes.add(MelodyNote(start, minOf(durationMs, start + 2000L), key, activeVel[key]))
            }
        }
        notes.sortBy { it.startMs }
        return notes
    }

    private class LongArrayList(capacity: Int = 256) {
        var data = LongArray(capacity)
        var size = 0
        fun add(value: Long) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun hasValueNear(target: Long, tolerance: Long): Boolean {
            var low = 0
            var high = size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val v = data[mid]
                if (abs(v - target) <= tolerance) return true
                if (v < target) low = mid + 1 else high = mid - 1
            }
            return false
        }
    }

    private data class Timing(val toUs: (Long) -> Long) {
        fun toMs(tick: Long): Long = toUs(tick) / 1000L
    }

    private fun buildTempoMap(input: List<Tempo>, division: Int): Timing {
        if (division and 0x8000 != 0) {
            var frames = ((division shr 8) and 0xff).toByte().toInt()
            if (frames == -29) frames = 30
            val fps = abs(frames).coerceAtLeast(1)
            val ticksPerFrame = (division and 0xff).coerceAtLeast(1)
            return Timing { tick -> tick * 1_000_000L / (fps * ticksPerFrame) }
        }
        val ppqn = (division and 0x7fff).coerceAtLeast(1)
        val points = input.sortedBy { it.tick }.fold(mutableListOf<Tempo>()) { out, item ->
            if (out.lastOrNull()?.tick == item.tick) out[out.lastIndex] = item else out += item
            out
        }
        val elapsedUsAt = LongArray(points.size)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            elapsedUsAt[i] = elapsedUsAt[i - 1] + (points[i].tick - prev.tick) * prev.microsPerQuarter.toLong() / ppqn
        }
        return Timing { tick ->
            var low = 0
            var high = points.lastIndex
            var idx = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (points[mid].tick <= tick) {
                    idx = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            elapsedUsAt[idx] + (tick - points[idx].tick) * points[idx].microsPerQuarter.toLong() / ppqn
        }
    }

    private fun readVlqSafe(bytes: ByteArray, cursor: Cursor, limit: Int): Int? {
        var value = 0
        repeat(4) {
            if (cursor.position >= limit) return null
            val byte = bytes[cursor.position++].toInt() and 0xff
            value = (value shl 7) or (byte and 0x7f)
            if (byte and 0x80 == 0) return value
        }
        return value
    }

    private fun decodeSmart(bytes: ByteArray, start: Int, length: Int): String {
        val slice = ByteBuffer.wrap(bytes, start, length)
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = try {
            utf8Decoder.decode(slice).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, start, length, windows1252)
        }
        return raw.replace("\u0000", "")
    }

    /**
     * Checks whether a raw meta-event is a KAR/MIDI header command (e.g. `@KMIDI`, `@T`, `@V`,
     * copyright notice) rather than a lyric or line-break event.
     * IMPORTANT: Does NOT reject standalone `\r`, `\n`, `/`, `\`, or `" "` events!
     */
    private fun isMetadataHeader(raw: String, tick: Long, isTextMeta: Boolean): Boolean {
        if (raw.isEmpty()) return true
        val trimmed = raw.trim()
        // If raw is non-empty but trimmed is empty, it is a standalone line break (\r, \n) or word space (" ")
        if (trimmed.isEmpty()) return false

        val upper = trimmed.uppercase()
        if (upper.startsWith("@KMIDI") || upper.startsWith("@K") || upper.startsWith("@V") ||
            upper.startsWith("@I") || upper.startsWith("@L") || upper.startsWith("@W") ||
            upper.startsWith("@T") || upper.startsWith("KARAOKE") || upper.startsWith("SEQUENCED BY") ||
            upper.startsWith("COPYRIGHT") || upper.startsWith("MIDI BY") ||
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") || upper.startsWith("WWW.") ||
            upper.startsWith("CREATED BY") || upper.startsWith("ARRANGED BY")
        ) {
            return true
        }
        // In 0x01 (Text) events at tick 0, filter out instrument/track labels
        if (isTextMeta && tick == 0L && (
                upper.startsWith("TRACK") || upper.startsWith("MELODY") ||
                    upper.startsWith("VOCAL") || upper.startsWith("PIANO") ||
                    upper.startsWith("GUITAR") || upper.startsWith("BASS") ||
                    upper.startsWith("DRUMS") || upper.startsWith("STRINGS")
                )
        ) {
            return true
        }
        return false
    }

    private fun ascii(bytes: ByteArray, start: Int, length: Int) =
        String(bytes, start, length, Charsets.US_ASCII)

    private fun u16(bytes: ByteArray, pos: Int) =
        ((bytes[pos].toInt() and 0xff) shl 8) or (bytes[pos + 1].toInt() and 0xff)

    private fun u32(bytes: ByteArray, pos: Int) =
        ((bytes[pos].toLong() and 0xff) shl 24) or
            ((bytes[pos + 1].toLong() and 0xff) shl 16) or
            ((bytes[pos + 2].toLong() and 0xff) shl 8) or
            (bytes[pos + 3].toLong() and 0xff)
}
