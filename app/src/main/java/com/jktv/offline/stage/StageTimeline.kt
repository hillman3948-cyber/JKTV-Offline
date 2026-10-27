package com.jktv.offline.stage

import com.jktv.offline.playback.LyricLine

/** Shared host/receiver rules. Positions are song milliseconds, including host lyric calibration. */
object StageTimeline {
    /** Retain every timing boundary, while preserving the host word boundaries in display text. */
    fun presentationLines(lines: List<LyricLine>, grouping: String): List<LyricLine> {
        val normalized = lines.map { line ->
            if (line.words.isEmpty()) line else {
                val text = StringBuilder()
                val syllables = mutableListOf<com.jktv.offline.playback.LyricSyllable>()
                line.words.forEach { word ->
                    val visible = word.text.trim()
                    if (visible.isNotEmpty()) {
                        if (text.isNotEmpty()) text.append(' ')
                        val start = text.length
                        val leading = word.text.length - word.text.trimStart().length
                        text.append(visible)
                        word.syllables.forEach { s ->
                            val from = (s.charStart - word.charStart - leading).coerceIn(0, visible.length)
                            val to = (s.charEnd - word.charStart - leading).coerceIn(from, visible.length)
                            if (to > from) syllables.add(s.copy(text = visible.substring(from, to), charStart = start + from, charEnd = start + to))
                        }
                    }
                }
                LyricLine(line.index, line.startMs, line.endMs, text.toString(), syllables)
            }
        }
        if (grouping != "NATURAL_FULL") return normalized
        val phrases = mutableListOf<LyricLine>()
        normalized.forEach { line ->
            val previous = phrases.lastOrNull()
            val merge = previous != null && previous.text.length < 18 && previous.text.length + line.text.length < 56 &&
                line.startMs - previous.endMs in 0L..600L && previous.text.lastOrNull() !in listOf('.', '?', '!', ';')
            if (merge) {
                val base = previous!!; val offset = base.text.length + 1
                phrases[phrases.lastIndex] = LyricLine(base.index, base.startMs, line.endMs,
                    base.text + " " + line.text, base.syllables + line.syllables.map { it.copy(charStart = it.charStart + offset, charEnd = it.charEnd + offset) })
            } else phrases.add(line.copy(index = phrases.size))
        }
        return phrases
    }
    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        for (i in lines.indices) {
            val end = minOf(lines[i].endMs + 160, (lines.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE) - 1)
            if (positionMs in lines[i].startMs..end) return i
        }
        val next = lines.indexOfFirst { it.startMs > positionMs }
        return when {
            next == 0 -> 0
            next > 0 -> if (positionMs >= lines[next - 1].endMs + 160 || lines[next].startMs - positionMs <= 650) next else next - 1
            else -> lines.lastIndex
        }
    }
}
