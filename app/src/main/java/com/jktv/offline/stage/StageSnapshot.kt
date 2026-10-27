package com.jktv.offline.stage

import com.jktv.offline.LibraryUiState
import com.jktv.offline.cast.StageCodec
import com.jktv.offline.playback.PlaybackState
import org.json.JSONArray
import org.json.JSONObject

/** The phone owns this complete configuration and clock. Receivers keep no independent preferences. */
object StageSnapshot {
    private var cachedState: LibraryUiState? = null
    private var cachedSettings = JSONObject()
    private var cachedRevision = ""
    @Synchronized private fun configuration(s:LibraryUiState):Pair<JSONObject,String> {
        if(cachedState !== s){cachedState=s;cachedSettings=StageCodec.settings(s);cachedRevision=cachedSettings.toString().hashCode().toString()}
        return cachedSettings to cachedRevision
    }
    fun encode(s: LibraryUiState, p: PlaybackState, session: String, knownLyrics: String, knownSettings: String): JSONObject {
        val sampled = System.nanoTime()
        val (settings,settingsRevision) = configuration(s)
        val lyricsRevision = "$session:${System.identityHashCode(p.lyricLines)}:${p.lyricLines.size}:${p.syncSourceType}:${p.syncConfidence}"
        val raw = if (p.playing && p.clockAnchorWallNs > 0) p.clockAnchorSongUs / 1000 +
            (((sampled - p.clockAnchorWallNs).coerceIn(0, 80_000_000) / 1_000_000.0) * p.tempoMultiplier).toLong() else p.positionMs
        return JSONObject().put("protocol", 3).put("session", session)
            .put("song", s.currentSong?.id?.toString() ?: p.file?.absolutePath.orEmpty())
            .put("playing", p.playing).put("position", p.syncedLyricPositionMs(sampled))
            .put("lyricOffset", p.effectiveLatencyOffsetMs)
            .put("rawPosition", raw.coerceIn(0, p.durationMs.coerceAtLeast(0)))
            .put("duration", p.durationMs).put("tempo", p.tempoMultiplier)
            .put("sampleTime", sampled / 1_000_000.0).put("transportRevision", p.transportRevision)
            .put("revision", lyricsRevision).put("settingsRevision", settingsRevision)
            .put("activeLine", StageTimeline.activeLineIndex(p.lyricLines, p.syncedLyricPositionMs(sampled)))
            .put("source", p.syncSourceType.name).put("levels", JSONArray(p.channelLevels))
            .apply {
                if (knownSettings != settingsRevision) put("settings", settings)
                if (knownLyrics != lyricsRevision) put("lyrics", StageCodec.lyrics(p))
            }
    }
}
