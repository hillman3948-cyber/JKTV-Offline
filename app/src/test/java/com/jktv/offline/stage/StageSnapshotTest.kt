package com.jktv.offline.stage

import com.jktv.offline.LibraryUiState
import com.jktv.offline.cast.StageCodec
import com.jktv.offline.data.SongEntity
import com.jktv.offline.playback.*
import com.jktv.offline.ui.VisualizerStyles
import com.jktv.offline.ui.LyricPalettes
import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class StageSnapshotTest {
    private val song = SongEntity(id=1, title="'Di Ak Nahuhulop", artist="Mabuhay Singers", relativePath="test.mid")
    private val base = LibraryUiState(currentSong=song)
    private val lines = listOf(
        LyricLine(0,1000,6000,"An aso waray kitaa",listOf(
            LyricSyllable("An",1000,1800,0,2),LyricSyllable("aso",1800,2700,3,6),
            LyricSyllable("waray",2700,4000,7,12),LyricSyllable("kitaa",4000,6000,13,18))),
        LyricLine(1,7000,11500,"Di ak nahuhulop",listOf(LyricSyllable("Di",7000,7800,0,2),
            LyricSyllable("ak",7800,8600,3,5),LyricSyllable("nahuhulop",8600,11500,6,15))),
        LyricLine(2,16000,21000,"The phone stays in control",listOf(LyricSyllable("The phone stays in control",16000,21000,0,25)))
    )
    private val playback = PlaybackState(file=File("test.mid"),positionMs=3200,durationMs=25000,
        lyricLines=lines,syncSourceType=SyncSourceType.USER_SYNCED_SIDECAR,syncConfidence=SyncConfidence.ACCURATE_SYLLABLE)
    private fun frame(s:LibraryUiState=base) = StageSnapshot.encode(s,playback,"test-session","","")
    private fun changes():List<Pair<String,LibraryUiState>> = listOf(
            "stageGuestQrEnabled" to base.copy(stageGuestQrEnabled = false),
            "lyricFontSizeScale" to base.copy(lyricFontSizeScale = 1.6f),
            "lyricPaletteName" to base.copy(lyricPaletteName = "CUSTOM_PALETTE"),
            "visualizerStyle" to base.copy(visualizerStyle = "AURORA_WAVES"),
            "twoLineKaraokeLayout" to base.copy(twoLineKaraokeLayout = false),
            "lyricFontFamily" to base.copy(lyricFontFamily = "SERIF"),
            "lyricFontWeight" to base.copy(lyricFontWeight = "MEDIUM"),
            "lyricLetterSpacing" to base.copy(lyricLetterSpacing = 2f),
            "lyricLineSpacing" to base.copy(lyricLineSpacing = 23f),
            "lyricTextAlignment" to base.copy(lyricTextAlignment = "LEFT"),
            "lyricStagePosition" to base.copy(lyricStagePosition = "BOTTOM"),
            "lyricOutlineThickness" to base.copy(lyricOutlineThickness = 3f),
            "lyricShadowIntensity" to base.copy(lyricShadowIntensity = 0f),
            "lyricDisplayMode" to base.copy(lyricDisplayMode = "FOUR_LINE"),
            "lyricPhraseGrouping" to base.copy(lyricPhraseGrouping = "RAW_MIDI"),
            "lyricsEnabled" to base.copy(lyricsEnabled = false),
            "countdownEnabled" to base.copy(countdownEnabled = false),
            "songInfoEnabled" to base.copy(songInfoEnabled = false),
            "singerInfoEnabled" to base.copy(singerInfoEnabled = false),
            "lyricWipeEnabled" to base.copy(lyricWipeEnabled = false),
            "backgroundIsImage" to base.copy(backgroundIsImage = true),
            "backgroundBrightness" to base.copy(backgroundBrightness = .6f),
            "lyricsScrimAlpha" to base.copy(lyricsScrimAlpha = .7f),
            "visualizerSensitivity" to base.copy(visualizerSensitivity = 1.7f),
            "visualizerSpeed" to base.copy(visualizerSpeed = 1.8f),
            "visualizerBrightness" to base.copy(visualizerBrightness = .5f),
            "visualizerIntensityPreset" to base.copy(visualizerIntensityPreset = "IMMERSIVE"),
            "visualizerLyricsSafeZone" to base.copy(visualizerLyricsSafeZone = false),
            "visualizerGlowStrength" to base.copy(visualizerGlowStrength = 1.6f),
            "visualizerPerformanceMode" to base.copy(visualizerPerformanceMode = false),
            "customActiveFillHex" to base.copy(customActiveFillHex = "#12AB34"),
            "customActiveGlowHex" to base.copy(customActiveGlowHex = "#FFAABB"),
            "customUnsungFillHex" to base.copy(customUnsungFillHex = "#F1F4FA"),
            "customNextLineFillHex" to base.copy(customNextLineFillHex = "#DDCCBB"),
            "karaokeMode" to base.copy(karaokeMode = "DUET")
    )
    @Test fun everyHostSettingIsEncodedAndRoundTripsToNativeReceiver() {
        changes().forEach { (key,state) ->
            val encoded=StageCodec.settings(state)
            assertTrue(key,encoded.has(key))
            val decoded=StageCodec.library(encoded)
            val field=LibraryUiState::class.java.getDeclaredField(key).apply { isAccessible=true }
            assertEquals(key,field.get(state),field.get(decoded))
        }
    }
    @Test fun eachIndividualChangeInvalidatesSnapshotSettingsAndRestoresAfterReconnect() {
        val initial=frame()
        changes().forEach { (key,state) ->
            // Some case values equal the current defaults; the complete payload still round-trips.
            val update=StageSnapshot.encode(state,playback,"test-session",initial.getString("revision"),"")
            assertTrue(key,update.has("settings"));assertFalse(update.has("lyrics"))
            val resumed=StageSnapshot.encode(state,playback.copy(positionMs=9000),"test-session","","")
            assertEquals(update.getJSONObject("settings").toString(),resumed.getJSONObject("settings").toString())
            assertTrue(resumed.has("lyrics"));assertEquals(8977L,resumed.getLong("position"))
        }
    }
    @Test fun clockAndSettingsUseIndependentRevisions() {
        val initial=frame()
        val update=StageSnapshot.encode(base.copy(lyricFontWeight="LIGHT"),playback.copy(transportRevision=42),
            "test-session",initial.getString("revision"),initial.getString("settingsRevision"))
        assertTrue(update.has("settings"));assertFalse(update.has("lyrics"));assertEquals(42L,update.getLong("transportRevision"))
        assertEquals(initial.getString("revision"),update.getString("revision"))
    }
    @Test fun androidArgbColorsAreConvertedToBrowserRgbaWithoutChangingChannels() {
        val palette=StageCodec.settings(base.copy(lyricPaletteName="CUSTOM_PALETTE",customActiveFillHex="#80123456")).getJSONObject("palette")
        assertEquals("rgba(18,52,86,0.5019607843137255)",palette.getString("activeFill"))
    }
    @Test fun lineRulesMatchHostAtCountdownInterludesAndLastLine() {
        assertEquals(0,StageTimeline.activeLineIndex(lines,0))
        assertEquals(0,StageTimeline.activeLineIndex(lines,3200))
        assertEquals(1,StageTimeline.activeLineIndex(lines,6300))
        assertEquals(2,StageTimeline.activeLineIndex(lines,12000))
        assertEquals(2,StageTimeline.activeLineIndex(lines,24000))
        assertEquals(-1,StageTimeline.activeLineIndex(emptyList(),0))
    }
    @Test fun wordSpacingAndPhraseChangesNeverChangeSyllableTimes() {
        val compact=LyricLine(0,1000,4000,"Anaso",listOf(LyricSyllable("An",1000,2000,0,2),LyricSyllable("aso",2000,4000,2,5)))
        // Real contiguous syllables are kept intact; explicit word gaps are preserved verbatim.
        val spaced=StageTimeline.presentationLines(lines,"RAW_MIDI")
        assertEquals("An aso waray kitaa",spaced[0].text)
        assertEquals(lines.flatMap { it.syllables }.map { it.startMs to it.endMs },
            spaced.flatMap { it.syllables }.map { it.startMs to it.endMs })
        val raw=listOf(LyricLine(0,0,1000,"Hello",listOf(LyricSyllable("Hello",0,1000,0,5))),
            LyricLine(1,1200,2200,"world",listOf(LyricSyllable("world",1200,2200,0,5))))
        assertEquals(2,StageTimeline.presentationLines(raw,"RAW_MIDI").size)
        val grouped=StageTimeline.presentationLines(raw,"NATURAL_FULL")
        assertEquals(1,grouped.size);assertEquals("Hello world",grouped[0].text)
        assertEquals(listOf(0L,1200L),grouped[0].syllables.map { it.startMs })
    }
    @Test fun exportCanonicalHostFixturesForBrowserAcceptanceMatrix() {
        val directory=File(System.getProperty("java.io.tmpdir"),"jktv-stage-fixtures").apply { mkdirs() }
        File(directory,"host.json").writeText(frame().toString())
        val variants=JSONArray()
        changes().forEach { (name,state)->variants.put(org.json.JSONObject().put("name",name).put("frame",frame(if (name.startsWith("custom")) state.copy(lyricPaletteName="CUSTOM_PALETTE") else state))) }
        listOf("DEFAULT","SERIF","MONOSPACE","CONDENSED","CASUAL","CURSIVE").forEach { family -> variants.put(org.json.JSONObject().put("name","font:"+family).put("frame",frame(base.copy(lyricFontFamily=family)))) }
        listOf("LIGHT","NORMAL","MEDIUM","SEMI_BOLD","BOLD","EXTRA_BOLD","BLACK").forEach { weight -> variants.put(org.json.JSONObject().put("name","weight:"+weight).put("frame",frame(base.copy(lyricFontWeight=weight)))) }
        VisualizerStyles.forEach { (id,_)->variants.put(org.json.JSONObject().put("name","visualizer:"+id).put("frame",frame(base.copy(visualizerStyle=id)))) }
        LyricPalettes.forEach { palette->variants.put(org.json.JSONObject().put("name","palette:"+palette.id).put("frame",frame(base.copy(lyricPaletteName=palette.id)))) }
        File(directory,"settings-cases.json").writeText(variants.toString())
    }
}

