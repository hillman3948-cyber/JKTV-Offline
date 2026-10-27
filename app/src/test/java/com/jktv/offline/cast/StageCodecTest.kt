package com.jktv.offline.cast

import com.jktv.offline.LibraryUiState
import com.jktv.offline.playback.*
import org.junit.Assert.*
import org.junit.Test

class StageCodecTest {
    @Test fun preservesCustomSyllableTimingAndUnicode() {
        val lines = listOf(LyricLine(0, 1000, 2000, "你好", listOf(
            LyricSyllable("你", 1000, 1500, 0, 1), LyricSyllable("好", 1500, 2000, 1, 2))))
        val playback = PlaybackState(lyricLines = lines, syncSourceType = SyncSourceType.USER_SYNCED_SIDECAR,
            syncConfidence = SyncConfidence.ACCURATE_SYLLABLE)
        val restored = StageCodec.decodeLyrics(StageCodec.lyrics(playback))
        assertEquals(lines, restored.lyricLines)
        assertEquals(playback.syncSourceType, restored.sourceType)
        assertEquals(playback.syncConfidence, restored.confidence)
    }

    @Test fun stageSettingsExcludePhoneNavigationAndPreserveCustomization() {
        val source = LibraryUiState(lyricFontSizeScale = 1.6f, visualizerStyle = "NEON_BARS",
            customActiveFillHex = "#123456", activeSingerName = "Singer", searchQuery = "private query",
            fullScreenStage = false)
        val encoded = StageCodec.settings(source)
        assertFalse(encoded.has("searchQuery")); assertFalse(encoded.has("queue"))
        val decoded = StageCodec.library(encoded)
        assertEquals(source.lyricFontSizeScale, decoded.lyricFontSizeScale)
        assertEquals(source.customActiveFillHex, decoded.customActiveFillHex)
        assertEquals(source.activeSingerName, decoded.activeSingerName)
    }
}
