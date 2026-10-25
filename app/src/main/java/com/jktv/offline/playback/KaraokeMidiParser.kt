package com.jktv.offline.playback

import java.io.File
import java.nio.charset.Charset

data class TimedLyricEvent(val timeMs: Long, val text: String, val beginsLine: Boolean)
data class MidiTimeline(val durationMs: Long, val lyrics: List<TimedLyricEvent>, val hasLyrics: Boolean, val hasEventTiming: Boolean)

/** Parses standard MIDI timing and lyric/text meta events without loading a synthesizer. */
object KaraokeMidiParser {
    private data class Tempo(val tick: Long, val microsPerQuarter: Int)
    private data class RawLyric(val tick: Long, val text: String)
    private data class Cursor(var position: Int)

    fun parse(file: File): MidiTimeline {
        val midi = parse(file.readBytes())
        val sidecar = File(file.parentFile, file.nameWithoutExtension + ".lrc")
        if (!sidecar.isFile) return midi
        val externalLyrics = runCatching { parseLrc(sidecar) }.getOrNull().orEmpty()
        if (externalLyrics.isEmpty()) return midi
        return midi.copy(lyrics = externalLyrics, hasLyrics = true, hasEventTiming = externalLyrics.size > 1)
    }

    /** Reads standard timestamped .lrc sidecars; a sidecar takes precedence over embedded text. */
    private fun parseLrc(file: File): List<TimedLyricEvent> {
        val contents = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val offsetMs = Regex("(?im)^\\s*\\[offset\\s*:\\s*([+-]?\\d+)\\s*]\\s*$")
            .find(contents)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        return contents.lineSequence().flatMap { line ->
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) return@flatMap emptySequence()
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isBlank()) return@flatMap emptySequence()
            tags.asSequence().mapNotNull { tag ->
                val minutes = tag.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val seconds = tag.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val fraction = tag.groupValues[3]
                val fractionMs = when (fraction.length) {
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    3 -> fraction.toLong()
                    else -> 0L
                }
                val timeMs = (minutes * 60_000L + seconds * 1_000L + fractionMs + offsetMs).coerceAtLeast(0L)
                TimedLyricEvent(timeMs, text, beginsLine = true)
            }
        }.sortedBy { it.timeMs }.toList()
    }

    fun parse(bytes: ByteArray): MidiTimeline {
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

        repeat(tracks) {
            require(cursor.position + 8 <= bytes.size && ascii(bytes, cursor.position, 4) == "MTrk") { "A MIDI track is missing or truncated." }
            val trackLength = u32(bytes, cursor.position + 4).toInt()
            cursor.position += 8
            val end = cursor.position.toLong() + trackLength
            require(trackLength >= 0 && end <= bytes.size) { "A MIDI track is truncated." }
            val trackEnd = end.toInt()
            var tick = 0L
            var runningStatus = -1
            while (cursor.position < trackEnd) {
                tick += readVlq(bytes, cursor, trackEnd).toLong()
                finalTick = maxOf(finalTick, tick)
                var status = bytes[cursor.position].toInt() and 0xff
                if (status and 0x80 != 0) { cursor.position++; runningStatus = status }
                else {
                    require(runningStatus >= 0) { "A MIDI event has no running status." }
                    status = runningStatus
                }

                when {
                    status == 0xff -> {
                        require(cursor.position < trackEnd) { "A MIDI meta event is truncated." }
                        val type = bytes[cursor.position++].toInt() and 0xff
                        val length = readVlq(bytes, cursor, trackEnd)
                        require(cursor.position.toLong() + length <= trackEnd) { "A MIDI meta event is truncated." }
                        val eventEnd = cursor.position + length
                        when {
                            type == 0x51 && length == 3 -> {
                                val micros = ((bytes[cursor.position].toInt() and 0xff) shl 16) or
                                    ((bytes[cursor.position + 1].toInt() and 0xff) shl 8) or
                                    (bytes[cursor.position + 2].toInt() and 0xff)
                                if (micros > 0) tempos += Tempo(tick, micros)
                            }
                            type == 0x05 -> lyricEvents += RawLyric(tick, decode(bytes, cursor.position, length))
                            type == 0x01 -> textEvents += RawLyric(tick, decode(bytes, cursor.position, length))
                        }
                        cursor.position = eventEnd
                        if (type == 0x2f) cursor.position = trackEnd
                    }
                    status == 0xf0 || status == 0xf7 -> {
                        val length = readVlq(bytes, cursor, trackEnd)
                        require(cursor.position.toLong() + length <= trackEnd) { "A MIDI system-exclusive event is truncated." }
                        cursor.position += length
                    }
                    status in 0x80..0xef -> {
                        val kind = status and 0xf0
                        val count = if (kind == 0xc0 || kind == 0xd0) 1 else 2
                        require(cursor.position + count <= trackEnd) { "A MIDI channel event is truncated." }
                        cursor.position += count
                    }
                    else -> throw IllegalArgumentException("The MIDI file contains an unsupported event.")
                }
            }
            cursor.position = trackEnd
        }

        val timing = buildTempoMap(tempos, division)
        val selected = (if (lyricEvents.any { it.text.isNotBlank() }) lyricEvents else textEvents)
            .filter { it.text.isNotBlank() }.sortedBy { it.tick }
        val lyrics = selected.mapIndexed { index, event ->
            val next = selected.getOrNull(index - 1)
            TimedLyricEvent(timing.toMs(event.tick), clean(event.text), next == null || containsBreak(next.text))
        }.filter { it.text.isNotBlank() }
        return MidiTimeline(timing.toMs(finalTick), lyrics, lyrics.isNotEmpty(), lyrics.size > 1)
    }

    private data class Timing(val toMs: (Long) -> Long)

    private fun buildTempoMap(input: List<Tempo>, division: Int): Timing {
        if (division and 0x8000 != 0) {
            var frames = ((division shr 8) and 0xff).toByte().toInt()
            if (frames == -29) frames = 30 // MIDI drop-frame timing is approximated at 30 fps.
            val fps = kotlin.math.abs(frames).coerceAtLeast(1)
            val ticksPerFrame = (division and 0xff).coerceAtLeast(1)
            return Timing { tick -> tick * 1000L / (fps * ticksPerFrame) }
        }
        val ppqn = division and 0x7fff
        require(ppqn > 0) { "The MIDI timing division is invalid." }
        val points = input.sortedBy { it.tick }.fold(mutableListOf<Tempo>()) { out, item ->
            if (out.lastOrNull()?.tick == item.tick) out[out.lastIndex] = item else out += item
            out
        }
        val elapsedAt = LongArray(points.size)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            elapsedAt[i] = elapsedAt[i - 1] + (points[i].tick - prev.tick) * prev.microsPerQuarter / ppqn / 1000L
        }
        return Timing { tick ->
            var index = points.lastIndex
            while (index > 0 && points[index].tick > tick) index--
            elapsedAt[index] + (tick - points[index].tick) * points[index].microsPerQuarter / ppqn / 1000L
        }
    }

    private fun readVlq(bytes: ByteArray, cursor: Cursor, limit: Int): Int {
        var value = 0
        repeat(4) {
            require(cursor.position < limit) { "A MIDI variable-length value is truncated." }
            val byte = bytes[cursor.position++].toInt() and 0xff
            value = (value shl 7) or (byte and 0x7f)
            if (byte and 0x80 == 0) return value
        }
        throw IllegalArgumentException("A MIDI variable-length value is invalid.")
    }

    private fun decode(bytes: ByteArray, start: Int, length: Int): String =
        String(bytes, start, length, Charsets.UTF_8).replace("\u0000", "").trim()

    private fun clean(value: String) = value.replace(Regex("[\\r\\n/\\\\]+"), " ").replace(Regex("\\s+"), " ").trim()
    private fun containsBreak(value: String) = value.any { it == '\n' || it == '\r' || it == '/' || it == '\\' }
    private fun ascii(bytes: ByteArray, start: Int, length: Int) = String(bytes, start, length, Charset.forName("US-ASCII"))
    private fun u16(bytes: ByteArray, pos: Int) = ((bytes[pos].toInt() and 0xff) shl 8) or (bytes[pos + 1].toInt() and 0xff)
    private fun u32(bytes: ByteArray, pos: Int) = ((bytes[pos].toLong() and 0xff) shl 24) or ((bytes[pos + 1].toLong() and 0xff) shl 16) or ((bytes[pos + 2].toLong() and 0xff) shl 8) or (bytes[pos + 3].toLong() and 0xff)
}
