package com.jktv.offline.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class PcmOutputGuardTest {
    @Test fun quietSignalIsUnchangedAfterExactlyOneBlock() {
        val guard = PcmOutputGuard()
        val block = FloatArray(512) { (sin(it * 0.1) * 0.2).toFloat() }
        assertTrue(guard.process(block.copyOf(), 1).all { it == 0f })
        assertArrayEquals(block, guard.process(FloatArray(512), 1), 0f)
        assertEquals(0L, guard.limitedBlocks)
    }

    @Test fun abruptOverloadAndReleaseStayBelowCeilingAndKeepStereoBalance() {
        val guard = PcmOutputGuard()
        val random = Random(12)
        var previous = FloatArray(512)
        repeat(1500) { block ->
            val amplitude = when (block % 5) { 0 -> 0.1f; 1 -> 8f; 2 -> 0f; else -> 1.5f }
            val input = FloatArray(512)
            for (i in 0 until 256) {
                input[2*i] = (random.nextFloat()*2-1) * amplitude
                input[2*i+1] = input[2*i] * -0.37f
            }
            val output = guard.process(input, 1)
            assertTrue(output.all { it.isFinite() && abs(it) <= PcmOutputGuard.CEILING + 0.000001f })
            for (i in 0 until 256) {
                assertEquals(output[2*i] * -0.37f, output[2*i+1], 0.000001f)
                if (previous[2*i] != 0f) assertTrue(abs(output[2*i]) <= abs(previous[2*i]) + 0.000001f)
            }
            previous = input
        }
        assertTrue(guard.limitedBlocks > 0)
    }

    @Test fun transportChangeDoesNotLeakOldAudioAndInvalidSamplesAreSanitized() {
        val guard = PcmOutputGuard()
        guard.process(FloatArray(512) { 0.5f }, 1)
        assertTrue(guard.process(FloatArray(512), 2).all { it == 0f })
        val invalid = FloatArray(512).also { it[0] = Float.NaN; it[1] = Float.POSITIVE_INFINITY }
        guard.process(invalid, 2)
        assertTrue(guard.process(FloatArray(512), 2).all { it == 0f })
        assertEquals(2L, guard.invalidSamples)
        assertEquals(0f, PcmOutputGuard.synthGain(0f), 0f)
        assertEquals(0.17f, PcmOutputGuard.synthGain(0.85f), 0.00001f)
        assertTrue(PcmOutputGuard.synthGain(Float.NaN).isFinite())
    }
}
