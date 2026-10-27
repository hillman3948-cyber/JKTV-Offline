package com.jktv.offline.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** Stereo-linked peak protection with one block of lookahead. Quiet audio is unchanged.
 * A ramp across the preceding block anticipates peaks without waveshaping or stereo shifts.
 * Both local float output and cast PCM16 MUST consume the returned samples.
 */
class PcmOutputGuard(private val frames: Int = 256, private val sampleRate: Int = 44100) {
    private val delayed = FloatArray(frames * 2)
    private val output = FloatArray(frames * 2)
    private var previousLimit = 1f
    private var gain = 1f
    private var revision = Long.MIN_VALUE
    private val release = exp(-frames.toDouble() / (sampleRate * 0.15)).toFloat()
    val latencyMs: Double get() = frames * 1000.0 / sampleRate
    var peakBefore = 0f
        private set
    var peakAfter = 0f
        private set
    var limitedBlocks = 0L
        private set
    var invalidSamples = 0L
        private set

    fun process(input: FloatArray, transportRevision: Long): FloatArray {
        require(input.size == delayed.size)
        if (revision != transportRevision) {
            delayed.fill(0f)
            previousLimit = 1f
            revision = transportRevision
        }
        var peak = 0f
        for (i in input.indices) {
            if (!input[i].isFinite()) { input[i] = 0f; invalidSamples++ }
            peak = maxOf(peak, abs(input[i]))
        }
        peakBefore = maxOf(peakBefore, peak)
        val nextLimit = if (peak > CEILING) CEILING / peak else 1f
        // Start and end gain are both <= the delayed block's permitted gain.
        // Next block's limit is reached BEFORE its first sample is emitted.
        val endGain = min(min(previousLimit, nextLimit), 1f - (1f - gain) * release)
        val startGain = gain
        for (frame in 0 until frames) {
            val g = startGain + (endGain - startGain) * ((frame + 1f) / frames)
            for (channel in 0..1) {
                val i = frame * 2 + channel
                output[i] = delayed[i] * g
                peakAfter = maxOf(peakAfter, abs(output[i]))
            }
        }
        if (endGain < 0.999f) limitedBlocks++
        input.copyInto(delayed)
        previousLimit = nextLimit
        gain = endGain
        return output
    }

    companion object {
        // About 1 dB of margin for conversion and downstream resampling.
        const val CEILING = 0.89f
        fun synthGain(master: Float): Float =
            (if (master.isFinite()) master.coerceIn(0f, 2.5f) else 0.85f) * 0.2f
    }
}

