package com.jktv.offline.cast

/** All positions are song milliseconds; all elapsed times are monotonic wall milliseconds. */
object StageClock {
    const val AUDIO_LEASE_MS = 2000L
    const val SEEK_THRESHOLD_MS = 150L
    fun target(position: Long, duration: Long, playing: Boolean, tempo: Float, transitMs: Long): Long =
        (position + if (playing) (transitMs.coerceIn(0, 250) * tempo.coerceIn(0.5f, 1.5f)).toLong() else 0)
            .coerceIn(0L, duration.coerceAtLeast(0L))

    fun needsSeek(local: Long, target: Long, playing: Boolean, newlyAudible: Boolean): Boolean =
        newlyAudible || kotlin.math.abs(target - local) > SEEK_THRESHOLD_MS || (!playing && target != local)
}
