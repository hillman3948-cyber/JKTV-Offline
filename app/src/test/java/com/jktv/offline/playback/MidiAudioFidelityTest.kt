package com.jktv.offline.playback

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class MidiAudioFidelityTest {
    private fun midi(vararg track: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeBytes("MThd"); it.writeInt(6); it.writeShort(0); it.writeShort(1); it.writeShort(480)
            it.writeBytes("MTrk"); it.writeInt(track.size)
            track.forEach { value -> it.writeByte(value) }
        }
        return bytes.toByteArray()
    }

    @Test fun keepsControllersBendAndPressureInOriginalOrderIncludingRunningStatus() {
        val parsed = KaraokeMidiParser.parse(midi(
            0, 0xb0, 0, 0, // bank MSB
            0, 32, 1, // bank LSB, running status
            0, 0xc0, 24,
            0, 0xb0, 7, 54,
            0, 11, 70,
            0, 64, 127,
            0, 0xe0, 0, 96,
            0, 0xd0, 45,
            0, 0xa0, 60, 67,
            0, 0x90, 60, 93,
            1, 0x80, 60, 0,
            0, 0xff, 0x2f, 0
        ))
        val events = parsed.midiEvents
        assertEquals(11, events.size)
        assertEquals(listOf(0, 32, 24, 7, 11, 64, 0, 45, 60, 60, 60), events.map { it.data1 })
        assertEquals(MidiEventType.PITCH_BEND, events[6].type)
        assertEquals(12288, events[6].data1 or (events[6].data2 shl 7))
        assertEquals(MidiEventType.CHANNEL_PRESSURE, events[7].type)
        assertEquals(MidiEventType.KEY_PRESSURE, events[8].type)
        assertEquals(93, events[9].data2) // Original note velocity is retained.
        assertTrue(events.last().timeUs > events.first().timeUs)
    }

    @Test fun groupFaderPreservesAndRestoresSourceHighResolutionVolume() {
        val mix = MidiChannelVolumes()
        mix.control(2, 7, 60)
        mix.control(2, 39, 71)
        val source = (60 shl 7) or 71
        assertEquals(source, mix.scaled(2, 1f))
        assertEquals((source * 0.5f).toInt() + 1, mix.scaled(2, 0.5f))
        assertEquals(0, mix.scaled(2, 0f))
        assertEquals(source, mix.scaled(2, 1f))
        assertEquals(100 shl 7, mix.scaled(3, 1f))
        mix.reset()
        assertEquals(100 shl 7, mix.scaled(2, 1f))
    }

    @Test fun faderIsBoundedWithoutCorruptingOtherChannels() {
        val mix = MidiChannelVolumes()
        mix.control(0, 7, 127); mix.control(0, 39, 127)
        assertEquals(16383, mix.scaled(0, 1.5f))
        assertEquals(0, mix.scaled(0, -2f))
        assertEquals(16383, mix.scaled(0, Float.NaN))
        assertEquals(100 shl 7, mix.scaled(1, 1f))
    }
}
