package com.jktv.offline.playback

import java.io.File
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val file: File? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null
)

/** Native synthesis boundary. A FluidSynth JNI implementation is still required before playback controls are enabled. */
interface MidiEngine {
    val state: StateFlow<PlaybackState>
    suspend fun setSoundFont(file: File)
    suspend fun load(file: File)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun setTranspose(semitones: Int)
    fun setTempo(percent: Int)
    fun setMasterVolume(percent: Int)
    fun setReverb(enabled: Boolean)
    fun setChorus(enabled: Boolean)
    fun release()
}
