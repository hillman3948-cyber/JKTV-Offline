package com.jktv.offline.playback

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSyncEngineTest {

    /**
     * Helper to build a valid Standard MIDI / KAR binary file in memory with configurable PPQ,
     * tempo changes, lyric/text meta events, and vocal melody note-on/note-off events.
     */
    private fun writeTempMidiFile(
        ppq: Int = 480,
        events: List<Pair<Long, ByteArray>>
    ): File = writeTempMultiTrackMidiFile(ppq = ppq, tracks = listOf(events))

    private fun writeTempMultiTrackMidiFile(
        ppq: Int = 480,
        tracks: List<List<Pair<Long, ByteArray>>>
    ): File {
        val midi = ByteArrayOutputStream()
        // MThd header
        midi.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt32(midi, 6)
        writeInt16(midi, if (tracks.size > 1) 1 else 0)
        writeInt16(midi, tracks.size)
        writeInt16(midi, ppq)

        for (events in tracks) {
            val sorted = events.sortedBy { it.first }
            val trackBytes = ByteArrayOutputStream()
            var lastTick = 0L
            for ((tick, payload) in sorted) {
                val delta = (tick - lastTick).coerceAtLeast(0L)
                lastTick = tick
                writeVarLen(trackBytes, delta)
                trackBytes.write(payload)
            }
            // End of Track meta event (FF 2F 00)
            writeVarLen(trackBytes, 0L)
            trackBytes.write(byteArrayOf(0xFF.toByte(), 0x2F.toByte(), 0x00))

            val trackData = trackBytes.toByteArray()
            midi.write("MTrk".toByteArray(Charsets.US_ASCII))
            writeInt32(midi, trackData.size)
            midi.write(trackData)
        }

        val temp = File.createTempFile("jktv_test_", ".kar")
        temp.deleteOnExit()
        temp.writeBytes(midi.toByteArray())
        return temp
    }

    private fun tempoEvent(bpm: Int): ByteArray {
        val mpqn = 60_000_000 / bpm
        return byteArrayOf(
            0xFF.toByte(),
            0x51.toByte(),
            0x03,
            ((mpqn shr 16) and 0xFF).toByte(),
            ((mpqn shr 8) and 0xFF).toByte(),
            (mpqn and 0xFF).toByte()
        )
    }

    private fun lyricMetaEvent(text: String, type: Int = 0x05): ByteArray {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val out = ByteArrayOutputStream()
        out.write(0xFF)
        out.write(type)
        writeVarLen(out, bytes.size.toLong())
        out.write(bytes)
        return out.toByteArray()
    }

    private fun noteOn(channel: Int, note: Int, velocity: Int = 100): ByteArray =
        byteArrayOf((0x90 or (channel and 0x0F)).toByte(), (note and 0x7F).toByte(), (velocity and 0x7F).toByte())

    private fun noteOff(channel: Int, note: Int): ByteArray =
        byteArrayOf((0x80 or (channel and 0x0F)).toByte(), (note and 0x7F).toByte(), 0x00)

    private fun writeVarLen(out: ByteArrayOutputStream, value: Long) {
        var buffer = value and 0x7FL
        var v = value shr 7
        while (v > 0L) {
            buffer = (buffer shl 8) or ((v and 0x7FL) or 0x80L)
            v = v shr 7
        }
        while (true) {
            out.write((buffer and 0xFFL).toInt())
            if ((buffer and 0x80L) != 0L) buffer = buffer shr 8 else break
        }
    }

    private fun writeInt32(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt16(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    @Test
    fun testKarSyllableTimingAndVocalNoteOffRefinement_NoTextLengthEstimation() {
        // 120 BPM => 500,000 us per quarter note (480 ticks = 500 ms)
        val file = writeTempMidiFile(
            ppq = 480,
            events = listOf(
                0L to tempoEvent(120),
                480L to lyricMetaEvent("Hel"),
                480L to noteOn(0, 60, 100),
                720L to noteOff(0, 60),        // Vocal note ends at 750 ms (before next syllable at 1000 ms)
                960L to lyricMetaEvent("lo "),
                960L to noteOn(0, 62, 100),
                1320L to noteOff(0, 62),       // Vocal note ends at 1375 ms
                1440L to lyricMetaEvent("World"),
                1440L to noteOn(0, 64, 100),
                1920L to noteOff(0, 64),       // Vocal note ends at 2000 ms
                2400L to lyricMetaEvent("/Sing "),
                2400L to noteOn(0, 65, 100),
                2880L to noteOff(0, 65)
            )
        )

        val timeline = KaraokeMidiParser.parse(file)
        assertTrue("Expected embedded timing to be detected", timeline.hasEventTiming)
        assertEquals(2, timeline.lyricLines.size)

        val firstLine = timeline.lyricLines[0]
        // "Hel" + "lo " + "World" must reconstruct as "Hello World" without a space inside "Hello"
        assertEquals("Hello World", firstLine.text)
        assertEquals(2, firstLine.words.size)
        assertEquals("Hello", firstLine.words[0].text)
        assertEquals("World", firstLine.words[1].text)
        assertEquals(3, firstLine.syllables.size)
        // Syllable 0 ("Hel"): starts at 500ms (tick 480), vocal note-off at 750ms (tick 720)
        assertEquals(500L, firstLine.syllables[0].startMs)
        assertEquals(750L, firstLine.syllables[0].endMs)
        assertFalse(firstLine.syllables[0].endsWord)
        // Syllable 1 ("lo"): starts at 1000ms (tick 960), vocal note-off at 1375ms (tick 1320)
        assertEquals(1000L, firstLine.syllables[1].startMs)
        assertEquals(1375L, firstLine.syllables[1].endMs)
        assertTrue(firstLine.syllables[1].endsWord)
        // Syllable 2 ("World"): starts at 1500ms (tick 1440), vocal note-off at 2000ms (tick 1920)
        assertEquals(1500L, firstLine.syllables[2].startMs)
        assertEquals(2000L, firstLine.syllables[2].endMs)
        assertTrue(firstLine.syllables[2].endsWord)
    }

    @Test
    fun testFragmentedWordReconstructionPreservesSyllableTimestamps() {
        // User requirement example: "I " + "kn" + "o" + "w, " + "yes " + "I " + "know" -> "I know, yes I know"
        // and standalone '\r' meta-events between lines
        val file = writeTempMidiFile(
            ppq = 480,
            events = listOf(
                0L to tempoEvent(120),
                0L to lyricMetaEvent("@KMIDI KARAOKE FILE"),
                0L to lyricMetaEvent("@TTest Song"),
                480L to lyricMetaEvent("I "),
                960L to lyricMetaEvent("kn"),
                1200L to lyricMetaEvent("o"),
                1440L to lyricMetaEvent("w, "),
                1920L to lyricMetaEvent("yes "),
                2400L to lyricMetaEvent("I "),
                2880L to lyricMetaEvent("kn"),
                3120L to lyricMetaEvent("ow"),
                3120L to lyricMetaEvent("\r"),
                3840L to lyricMetaEvent("We"),
                4080L to lyricMetaEvent("'"),
                4320L to lyricMetaEvent("re "),
                4800L to lyricMetaEvent("PAG~"),
                5040L to lyricMetaEvent("I"),
                5280L to lyricMetaEvent("BIG "),
                5760L to lyricMetaEvent("`YONG "),
                6240L to lyricMetaEvent("puso^ ")
            )
        )

        val timeline = KaraokeMidiParser.parse(file)
        assertTrue(timeline.hasEventTiming)
        assertEquals(2, timeline.lyricLines.size)

        val line1 = timeline.lyricLines[0]
        assertEquals("I know, yes I know", line1.text)
        assertEquals(listOf("I", "know,", "yes", "I", "know"), line1.words.map { it.text })

        // Verify "know," has 3 syllables ("kn", "o", "w,") with preserved timestamps
        val knowCommaWord = line1.words[1]
        assertEquals("know,", knowCommaWord.text)
        assertEquals(listOf("kn", "o", "w,"), knowCommaWord.syllables.map { it.text })
        assertEquals(1000L, knowCommaWord.syllables[0].startMs)
        assertEquals(1250L, knowCommaWord.syllables[1].startMs)
        assertEquals(1500L, knowCommaWord.syllables[2].startMs)

        // Verify every syllable's [charStart, charEnd] matches line1.text substring
        for (syl in line1.syllables) {
            assertEquals(syl.text, line1.text.substring(syl.charStart, syl.charEnd))
        }

        // Verify line 2: contractions ("We" + "'" + "re "), compound hyphen ("PAG~" + "I" + "BIG "),
        // leading backtick ("`YONG "), and control char ('^')
        val line2 = timeline.lyricLines[1]
        assertEquals("We're PAG-IBIG 'YONG puso", line2.text)
        assertEquals(listOf("We're", "PAG-IBIG", "'YONG", "puso"), line2.words.map { it.text })
    }

    @Test
    fun testMultiTrackDuplicateLyricsRecoversWordBoundaries() {
        // Simulates 000090 - Cry Me a River where Track 1 has syllables without spaces
        // and Track 2 has duplicate word events with trailing spaces and '^' markers
        val track1 = listOf(
            0L to tempoEvent(120),
            480L to lyricMetaEvent("No"),
            720L to lyricMetaEvent("w"),
            960L to lyricMetaEvent("you"),
            1200L to lyricMetaEvent("say"),
            1440L to lyricMetaEvent("that"),
            1680L to lyricMetaEvent("you'"),
            1920L to lyricMetaEvent("re"),
            2160L to lyricMetaEvent("so"),
            2400L to lyricMetaEvent("r"),
            2640L to lyricMetaEvent("ry"),
            2640L to lyricMetaEvent("\r")
        )
        val track2 = listOf(
            480L to lyricMetaEvent("Now^ "),
            960L to lyricMetaEvent("you^ "),
            1200L to lyricMetaEvent("say^ "),
            1440L to lyricMetaEvent("that "),
            1680L to lyricMetaEvent("you're "),
            2160L to lyricMetaEvent("sorry "),
            2640L to lyricMetaEvent("\r")
        )

        val file = writeTempMultiTrackMidiFile(ppq = 480, tracks = listOf(track1, track2))
        val timeline = KaraokeMidiParser.parse(file)
        assertEquals(1, timeline.lyricLines.size)
        val line = timeline.lyricLines[0]
        assertEquals("Now you say that you're sorry", line.text)
        assertEquals(listOf("Now", "you", "say", "that", "you're", "sorry"), line.words.map { it.text })
        // Still keeps all 10 syllable timing events from Track 1!
        assertEquals(10, line.syllables.size)
    }

    @Test
    fun testRealKaraokeMidiCorpusParsingAndWordReconstruction() {
        val corpusRoot = File("C:/xampp/htdocs/KARAOKE/data/media-trash")
        if (!corpusRoot.isDirectory) return

        val sampleFiles = corpusRoot.walkTopDown()
            .filter { it.isFile && (it.extension.equals("mid", true) || it.extension.equals("kar", true)) }
            .take(25)
            .toList()

        assertTrue("Expected real MIDI files in corpus", sampleFiles.isNotEmpty())

        for (midiFile in sampleFiles) {
            val timeline = KaraokeMidiParser.parse(midiFile)
            assertTrue("Expected lyrics in ${midiFile.name}", timeline.hasLyrics)
            assertTrue("Expected reconstructed lines in ${midiFile.name}", timeline.lyricLines.isNotEmpty())

            for (line in timeline.lyricLines) {
                // Verify no raw KAR headers or control chars leak into rendered lines
                assertFalse("No @KMIDI header in ${midiFile.name}", line.text.contains("@KMIDI", ignoreCase = true))
                assertFalse("No ^ control char in ${midiFile.name}: ${line.text}", line.text.contains('^'))
                assertFalse("No %+ control sequence in ${midiFile.name}: ${line.text}", line.text.contains("%+"))
                assertFalse("No double spaces in ${midiFile.name}: ${line.text}", line.text.contains("  "))

                // Verify every syllable's charStart..charEnd matches line.text
                for (syl in line.syllables) {
                    assertEquals(
                        "Syllable char range mismatch in ${midiFile.name} line '${line.text}'",
                        syl.text,
                        line.text.substring(syl.charStart, syl.charEnd)
                    )
                }

                // Verify words join with single spaces to equal line.text
                assertEquals(
                    "Reconstructed words mismatch in ${midiFile.name}",
                    line.text,
                    line.words.joinToString(" ") { it.text }
                )
            }
        }

        // Print diagnostic reports for key songs to stdout for inspection
        val highlightTargets = listOf("25 Minutes", "000004", "000051", "000090", "000106")
        for (code in highlightTargets) {
            val match = sampleFiles.firstOrNull { it.name.contains(code, ignoreCase = true) }
                ?: corpusRoot.walkTopDown().firstOrNull { it.isFile && it.name.contains(code, ignoreCase = true) }
            if (match != null) {
                println(KaraokeMidiParser.buildDiagnosticsReport(match).lines().take(20).joinToString("\n"))
                println("--------------------------------------------------------------------------------")
            }
        }
    }

    @Test
    fun testMidSongTempoChangesAndRepeatedChorusSynchronization() {
        // Segment 1: 0..480 ticks at 120 BPM (500 ms per beat)
        // Segment 2: 480..1440 ticks at 60 BPM (1000 ms per beat)
        // Segment 3: 1440..2400 ticks at 240 BPM (250 ms per beat)
        val file = writeTempMidiFile(
            ppq = 480,
            events = listOf(
                0L to tempoEvent(120),
                480L to lyricMetaEvent("Chorus "),
                480L to tempoEvent(60),
                960L to lyricMetaEvent("One"),
                1440L to tempoEvent(240),
                1920L to lyricMetaEvent("/Chorus "),
                2400L to lyricMetaEvent("Two")
            )
        )

        val timeline = KaraokeMidiParser.parse(file)
        assertTrue(timeline.hasEventTiming)
        assertEquals(2, timeline.lyricLines.size)

        // Tick 480 = 500 ms
        assertEquals(500L, timeline.lyricLines[0].syllables[0].startMs)
        // Tick 960 = 500 ms + 1 beat at 60 BPM (1000 ms) = 1500 ms
        assertEquals(1500L, timeline.lyricLines[0].syllables[1].startMs)
        // Tick 1920 = 500 ms + 2 beats at 60 BPM (2000 ms) + 1 beat at 240 BPM (250 ms) = 2750 ms
        assertEquals(2750L, timeline.lyricLines[1].syllables[0].startMs)
        // Tick 2400 = 2750 ms + 1 beat at 240 BPM (250 ms) = 3000 ms
        assertEquals(3000L, timeline.lyricLines[1].syllables[1].startMs)
    }

    @Test
    fun testLongIntroAndMidSongInterludeSkipDetection() {
        val lines = listOf(
            LyricLine(
                index = 0,
                text = "First Verse",
                startMs = 15_000L,
                endMs = 19_000L,
                syllables = listOf(LyricSyllable("First Verse", 15_000L, 19_000L, 0, 11))
            ),
            LyricLine(
                index = 1,
                text = "Second Verse After Solo",
                startMs = 35_000L,
                endMs = 40_000L,
                syllables = listOf(LyricSyllable("Second Verse After Solo", 35_000L, 40_000L, 0, 23))
            )
        )
        val timeline = MidiTimeline(
            durationMs = 45_000L,
            lyrics = emptyList(),
            hasLyrics = true,
            hasEventTiming = true,
            lyricLines = lines
        )

        // During 15s intro at 2000ms -> should offer skip to 15000 - 2200 = 12800ms
        val introSkip = timeline.interludeSkipTargetMs(2_000L)
        assertEquals(12_800L, introSkip)

        // During active singing at 16000ms -> no skip banner
        val singingSkip = timeline.interludeSkipTargetMs(16_000L)
        assertEquals(null, singingSkip)

        // During 16s guitar solo interlude at 20500ms -> should offer skip to 35000 - 2200 = 32800ms
        val soloSkip = timeline.interludeSkipTargetMs(20_500L)
        assertEquals(32_800L, soloSkip)
    }

    @Test
    fun testAuthoritativeClockSeekTempoAndBluetoothLatencyCalibration() {
        val cal = LatencyCalibration(
            globalOffsetMs = 40L,
            perSongOffsetMs = -20L,
            bluetoothOffsetMs = 180L,
            bluetoothAutoCompensation = true,
            bluetoothOutputDetected = true
        )
        // Total effective offset = 40 - 20 + 180 = +200 ms
        assertEquals(200L, cal.totalEffectiveOffsetMs)

        val anchorWallNs = 1_000_000_000L
        val state = PlaybackState(
            playing = true,
            positionMs = 10_000L,
            durationMs = 180_000L,
            tempoMultiplier = 1.25f,
            clockAnchorWallNs = anchorWallNs,
            clockAnchorSongUs = 10_000_000L, // 10,000 ms
            audioBufferLatencyMs = 23L,
            effectiveLatencyOffsetMs = cal.totalEffectiveOffsetMs
        )

        // Advance wall clock by 16 ms (16,000,000 ns) at 1.25x tempo => +20 ms song progression
        // Raw song pos = 10020 ms, minus 23 ms audio buffer latency, plus 200 ms user/BT calibration = 10197 ms
        val syncedPos = state.syncedLyricPositionMs(anchorWallNs + 16_000_000L)
        assertEquals(10_197L, syncedPos)
    }

    @Test
    fun testEnhancedLrcAndJsonRoundTrip() {
        val sampleLrc = """
            [ti:Test Song]
            [ar:JKTV Artist]
            [offset:120]
            [00:10.00]<00:10.00>Twin<00:10.30>kle <00:10.60>twinkle <00:11.30>lit<00:11.60>tle <00:12.00>star
            [00:14.00]How I wonder what you are
        """.trimIndent()

        val parsed = LyricsSyncEngine.parseLrc(sampleLrc, durationMs = 30_000L)
        assertNotNull(parsed)
        assertEquals(SyncSourceType.EXTERNAL_LRC, parsed.sourceType)
        assertEquals(SyncConfidence.ACCURATE_SYLLABLE, parsed.confidence)
        assertEquals(120L, parsed.perSongOffsetMs)
        assertEquals(2, parsed.lyricLines.size)
        assertEquals("Twinkle twinkle little star", parsed.lyricLines[0].text)
        assertEquals(listOf("Twinkle", "twinkle", "little", "star"), parsed.lyricLines[0].words.map { it.text })
        assertEquals(6, parsed.lyricLines[0].syllables.size)
        assertEquals(9_880L, parsed.lyricLines[0].syllables[0].startMs) // 10000ms - 120ms offset tag
        assertEquals(10_480L, parsed.lyricLines[0].syllables[2].startMs)

        // Export to Enhanced LRC and re-parse
        val exportedLrc = LyricsSyncEngine.exportEnhancedLrc("Test Song", "JKTV Artist", parsed.lyricLines, 0L)
        val reParsedLrc = LyricsSyncEngine.parseLrc(exportedLrc, durationMs = 30_000L)
        assertEquals(2, reParsedLrc.lyricLines.size)
        assertEquals("Twinkle twinkle little star", reParsedLrc.lyricLines[0].text)
        assertEquals(listOf("Twinkle", "twinkle", "little", "star"), reParsedLrc.lyricLines[0].words.map { it.text })
        assertEquals(10_480L, reParsedLrc.lyricLines[0].syllables[2].startMs)

        // Export to JKTV Sync JSON and re-parse
        val json = LyricsSyncEngine.exportJktvSyncJson(parsed.lyricLines, 120L)
        val parsedJson = LyricsSyncEngine.parseJktvSyncJson(json)
        assertEquals(2, parsedJson.lyricLines.size)
        assertEquals("Twinkle twinkle little star", parsedJson.lyricLines[0].text)
        assertEquals(listOf("Twinkle", "twinkle", "little", "star"), parsedJson.lyricLines[0].words.map { it.text })
        assertEquals(10_480L, parsedJson.lyricLines[0].syllables[2].startMs)
    }

    @Test
    fun testUntimedMidiDoesNotFalselyClaimAccurateTiming() {
        // All lyric events dumped at tick 0 (common in untimed MIDI files)
        val file = writeTempMidiFile(
            ppq = 480,
            events = listOf(
                0L to tempoEvent(120),
                0L to lyricMetaEvent("Line one text"),
                0L to lyricMetaEvent("/Line two text"),
                0L to lyricMetaEvent("/Line three text")
            )
        )

        val timeline = KaraokeMidiParser.parse(file)
        assertFalse(
            "Untimed lyric dump at tick 0 must NOT claim accurate embedded timing",
            timeline.hasEventTiming
        )
    }
}
