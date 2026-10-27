package com.jktv.offline.playback

import kotlin.math.roundToInt

/** Keep the song's 14-bit CC7/39 volume separate from the user's instrument-group fader.
 * Scaling volume, rather than note velocity, preserves velocity-sensitive sample layers.
 */
class MidiChannelVolumes {
    private val source = IntArray(16) { 100 shl 7 }
    fun reset() { source.fill(100 shl 7) }
    fun control(channel: Int, number: Int, value: Int) {
        val n = value.coerceIn(0, 127)
        source[channel] = when (number) {
            7 -> (n shl 7) or (source[channel] and 127)
            39 -> (source[channel] and 0x3f80) or n
            else -> source[channel]
        }
    }
    fun scaled(channel: Int, groupGain: Float): Int {
        val gain = if (groupGain.isFinite()) groupGain.coerceIn(0f, 1.5f) else 1f
        return (source[channel] * gain).roundToInt().coerceIn(0, 16383)
    }
}
