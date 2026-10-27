package com.jktv.offline.cast

import com.jktv.offline.LibraryUiState
import com.jktv.offline.playback.*
import com.jktv.offline.ui.resolveLyricPalette
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/** Canonical host Stage configuration, used by both native and browser receivers. */
object StageCodec {
    fun settings(s: LibraryUiState): JSONObject = JSONObject()
        .put("lyricFontSizeScale", s.lyricFontSizeScale)
        .put("lyricPaletteName", s.lyricPaletteName)
        .put("visualizerStyle", s.visualizerStyle)
        .put("twoLineKaraokeLayout", s.twoLineKaraokeLayout)
        .put("lyricFontFamily", s.lyricFontFamily)
        .put("lyricFontWeight", s.lyricFontWeight)
        .put("lyricLetterSpacing", s.lyricLetterSpacing)
        .put("lyricLineSpacing", s.lyricLineSpacing)
        .put("lyricTextAlignment", s.lyricTextAlignment)
        .put("lyricStagePosition", s.lyricStagePosition)
        .put("lyricOutlineThickness", s.lyricOutlineThickness)
        .put("lyricShadowIntensity", s.lyricShadowIntensity)
        .put("lyricDisplayMode", s.lyricDisplayMode)
        .put("lyricPhraseGrouping", s.lyricPhraseGrouping)
        .put("lyricsEnabled", s.lyricsEnabled)
        .put("stageGuestQrEnabled", s.stageGuestQrEnabled)
        .put("stageGuestJoinUrl", s.remoteServerState.guestUrl.ifBlank { s.stageGuestJoinUrl })
        .put("countdownEnabled", s.countdownEnabled)
        .put("songInfoEnabled", s.songInfoEnabled)
        .put("singerInfoEnabled", s.singerInfoEnabled)
        .put("lyricWipeEnabled", s.lyricWipeEnabled)
        .put("backgroundIsImage", s.backgroundIsImage)
        .put("backgroundBrightness", s.backgroundBrightness)
        .put("lyricsScrimAlpha", s.lyricsScrimAlpha)
        .put("visualizerSensitivity", s.visualizerSensitivity)
        .put("visualizerSpeed", s.visualizerSpeed)
        .put("visualizerBrightness", s.visualizerBrightness)
        .put("visualizerIntensityPreset", s.visualizerIntensityPreset)
        .put("visualizerLyricsSafeZone", s.visualizerLyricsSafeZone)
        .put("visualizerGlowStrength", s.visualizerGlowStrength)
        .put("visualizerPerformanceMode", s.visualizerPerformanceMode)
        .put("customActiveFillHex", s.customActiveFillHex)
        .put("customActiveGlowHex", s.customActiveGlowHex)
        .put("customUnsungFillHex", s.customUnsungFillHex)
        .put("customNextLineFillHex", s.customNextLineFillHex)
        .put("karaokeMode", s.karaokeMode)
        .put("singer", s.currentQueueRow?.singer ?: s.activeSingerName)
        .put("partner", s.currentQueueRow?.secondSinger ?: s.activeDuetPartner)
        .put("title", s.currentSong?.title.orEmpty()).put("artist", s.currentSong?.artist.orEmpty())
        .put("backgroundRevision", s.backgroundVideoUri?.hashCode()?.toString().orEmpty())
        .put("hasBackground", s.backgroundVideoUri != null)
        .put("visualizerSecondaryColor", "#FF2A85")
        .put("palette", palette(s))
        .put("key", s.keyTranspose).put("melody", s.melodyEnabled)
        .put("mixer", JSONObject().put("gain", s.mixerConfig.masterGain)
            .put("melody", s.mixerConfig.melodyVolume).put("drums", s.mixerConfig.drumsVolume)
            .put("bass", s.mixerConfig.bassVolume).put("accompaniment", s.mixerConfig.accompanimentVolume)
            .put("reverb", s.mixerConfig.reverbLevel).put("room", s.mixerConfig.reverbRoomSize)
            .put("chorus", s.mixerConfig.chorusLevel).put("quality", s.mixerConfig.highQualityInterpolation))

    private fun palette(s: LibraryUiState): JSONObject {
        val base = resolveLyricPalette(s.lyricPaletteName)
        fun hex(color: androidx.compose.ui.graphics.Color) = "#%06X".format(java.util.Locale.ROOT, color.toArgb() and 0xffffff)
        fun chosen(value: String, fallback: androidx.compose.ui.graphics.Color): String {
            if (s.lyricPaletteName != "CUSTOM_PALETTE") return hex(fallback)
            if (Regex("#[0-9a-fA-F]{6}").matches(value)) return value
            if (Regex("#[0-9a-fA-F]{8}").matches(value)) {
                val argb = value.substring(1).toLong(16)
                return "rgba(${(argb shr 16) and 255},${(argb shr 8) and 255},${argb and 255},${((argb shr 24) and 255) / 255.0})"
            }
            return hex(fallback)
        }
        return JSONObject().put("activeFill", chosen(s.customActiveFillHex, base.activeFill))
            .put("activeGlow", chosen(s.customActiveGlowHex, base.activeGlow))
            .put("unsungFill", chosen(s.customUnsungFillHex, base.unsungFill))
            .put("nextLineFill", chosen(s.customNextLineFillHex, base.nextLineFill))
            .put("outlineColor", hex(base.outlineColor)).put("isRainbow", base.isRainbow)
    }

    fun library(j: JSONObject): LibraryUiState = LibraryUiState(
        lyricFontSizeScale = j.getDouble("lyricFontSizeScale").toFloat(),
        lyricPaletteName = j.getString("lyricPaletteName"),
        visualizerStyle = j.getString("visualizerStyle"),
        twoLineKaraokeLayout = j.getBoolean("twoLineKaraokeLayout"),
        lyricFontFamily = j.getString("lyricFontFamily"),
        lyricFontWeight = j.getString("lyricFontWeight"),
        lyricLetterSpacing = j.getDouble("lyricLetterSpacing").toFloat(),
        lyricLineSpacing = j.getDouble("lyricLineSpacing").toFloat(),
        lyricTextAlignment = j.getString("lyricTextAlignment"),
        lyricStagePosition = j.getString("lyricStagePosition"),
        lyricOutlineThickness = j.getDouble("lyricOutlineThickness").toFloat(),
        lyricShadowIntensity = j.getDouble("lyricShadowIntensity").toFloat(),
        lyricDisplayMode = j.getString("lyricDisplayMode"),
        lyricPhraseGrouping = j.getString("lyricPhraseGrouping"),
        lyricsEnabled = j.getBoolean("lyricsEnabled"),
        stageGuestQrEnabled = j.optBoolean("stageGuestQrEnabled", true),
        stageGuestJoinUrl = j.optString("stageGuestJoinUrl"),
        countdownEnabled = j.getBoolean("countdownEnabled"),
        songInfoEnabled = j.getBoolean("songInfoEnabled"),
        singerInfoEnabled = j.getBoolean("singerInfoEnabled"),
        lyricWipeEnabled = j.getBoolean("lyricWipeEnabled"),
        backgroundIsImage = j.getBoolean("backgroundIsImage"),
        backgroundBrightness = j.getDouble("backgroundBrightness").toFloat(),
        lyricsScrimAlpha = j.getDouble("lyricsScrimAlpha").toFloat(),
        visualizerSensitivity = j.getDouble("visualizerSensitivity").toFloat(),
        visualizerSpeed = j.getDouble("visualizerSpeed").toFloat(),
        visualizerBrightness = j.getDouble("visualizerBrightness").toFloat(),
        visualizerIntensityPreset = j.getString("visualizerIntensityPreset"),
        visualizerLyricsSafeZone = j.getBoolean("visualizerLyricsSafeZone"),
        visualizerGlowStrength = j.getDouble("visualizerGlowStrength").toFloat(),
        visualizerPerformanceMode = j.getBoolean("visualizerPerformanceMode"),
        customActiveFillHex = j.getString("customActiveFillHex"),
        customActiveGlowHex = j.getString("customActiveGlowHex"),
        customUnsungFillHex = j.getString("customUnsungFillHex"),
        customNextLineFillHex = j.getString("customNextLineFillHex"),
        karaokeMode = j.getString("karaokeMode"),
        activeSingerName = j.getString("singer"), activeDuetPartner = j.getString("partner")
    )

    fun mixer(j: JSONObject) = AudioMixerConfig(
        masterGain = j.getDouble("gain").toFloat(), melodyVolume = j.getDouble("melody").toFloat(),
        drumsVolume = j.getDouble("drums").toFloat(), bassVolume = j.getDouble("bass").toFloat(),
        accompanimentVolume = j.getDouble("accompaniment").toFloat(),
        reverbLevel = j.getDouble("reverb").toFloat(), reverbRoomSize = j.getDouble("room").toFloat(),
        chorusLevel = j.getDouble("chorus").toFloat(), highQualityInterpolation = j.getBoolean("quality")
    )

    private fun syllables(items: List<LyricSyllable>) = JSONArray().apply { items.forEach { s ->
        put(JSONArray().put(s.text).put(s.startMs).put(s.endMs).put(s.charStart).put(s.charEnd).put(s.endsWord))
    } }

    fun lyrics(p: PlaybackState): JSONObject = JSONObject()
        .put("source", p.syncSourceType.name).put("confidence", p.syncConfidence.name)
        .put("lines", JSONArray().apply { p.lyricLines.forEach { line ->
            put(JSONObject().put("index", line.index).put("start", line.startMs).put("end", line.endMs)
                .put("text", line.text).put("syllables", syllables(line.syllables))
                .put("words", JSONArray().apply { line.words.forEach { word ->
                    put(JSONObject().put("text", word.text).put("start", word.startMs).put("end", word.endMs)
                        .put("from", word.charStart).put("to", word.charEnd).put("syllables", syllables(word.syllables)))
                } }))
        } })

    fun decodeLyrics(j: JSONObject): ResolvedLyricsSync {
        val lines = j.getJSONArray("lines")
        require(lines.length() <= 10000)
        return ResolvedLyricsSync(
            List(lines.length()) { i ->
                val l = lines.getJSONObject(i)
                val syllables = l.getJSONArray("syllables")
                require(syllables.length() <= 10000)
                LyricLine(l.getInt("index"), l.getLong("start"), l.getLong("end"), l.getString("text"),
                    List(syllables.length()) { k -> val s = syllables.getJSONArray(k)
                        LyricSyllable(s.getString(0), s.getLong(1), s.getLong(2), s.getInt(3), s.getInt(4), s.getBoolean(5))
                    })
            }, emptyList(), SyncSourceType.valueOf(j.getString("source")), SyncConfidence.valueOf(j.getString("confidence"))
        )
    }
}

