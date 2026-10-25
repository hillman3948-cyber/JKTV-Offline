package com.jktv.offline.playback

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeMidiParserTest {
    @Test
    fun sameNameLrcOverridesMidiAndAppliesOffsetAndMultipleTimestamps() {
        val directory = Files.createTempDirectory("jktv-lrc-test").toFile()
        try {
            val midi = File(directory, "Song.mid").apply { writeBytes(minimalMidi()) }
            File(directory, "Song.lrc").writeText(
                "[ar:Test Artist]\n[offset:-100]\n[00:01.2][00:03.40]First line\n[00:04.05]Second line\n",
                Charsets.UTF_8
            )

            val result = KaraokeMidiParser.parse(midi)

            assertTrue(result.hasLyrics)
            assertTrue(result.hasEventTiming)
            assertEquals(listOf("First line", "First line", "Second line"), result.lyrics.map { it.text })
            assertEquals(listOf(1_100L, 3_300L, 3_950L), result.lyrics.map { it.timeMs })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun midiWithoutLrcStillParsesEmbeddedLyricEvents() {
        val directory = Files.createTempDirectory("jktv-midi-test").toFile()
        try {
            val midi = File(directory, "Embedded.kar").apply { writeBytes(midiWithLyrics()) }
            val result = KaraokeMidiParser.parse(midi)

            assertTrue(result.hasLyrics)
            assertEquals(listOf("Hello", "world"), result.lyrics.map { it.text })
            assertEquals(listOf(0L, 500L), result.lyrics.map { it.timeMs })
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun minimalMidi(): ByteArray = midiTrack(byteArrayOf(0x00, 0xff.toByte(), 0x2f, 0x00))

    private fun midiWithLyrics(): ByteArray = midiTrack(
        byteArrayOf(
            0x00, 0xff.toByte(), 0x05, 0x05, 'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(),
            0x83.toByte(), 0x60, 0xff.toByte(), 0x05, 0x05, 'w'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'l'.code.toByte(), 'd'.code.toByte(),
            0x00, 0xff.toByte(), 0x2f, 0x00
        )
    )

    private fun midiTrack(track: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write("MThd".toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, 1, 0xE0.toByte()))
        write("MTrk".toByteArray(Charsets.US_ASCII))
        write(byteArrayOf(0, 0, 0, track.size.toByte()))
        write(track)
    }.toByteArray()
}
