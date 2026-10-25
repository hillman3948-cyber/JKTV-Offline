package com.jktv.offline.playback

import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.Interpolation
import dev.kotlinds.fluidsynthkmp.MidiFilePlayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val file: File? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<TimedLyricEvent> = emptyList(),
    val error: String? = null
)

/** Android FluidSynth playback using the installed SF2 bank and local MIDI/KAR files. */
class FluidSynthMidiEngine : MidiEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var soundFont: File? = null
    private var player: MidiFilePlayer? = null
    private var timeline: MidiTimeline? = null
    private var ticker: Job? = null

    override suspend fun setSoundFont(file: File) {
        require(file.isFile && file.length() > 0L) { "The installed SoundFont is missing or empty." }
        soundFont = file
    }

    override suspend fun load(file: File) {
        releasePlayer()
        try {
            require(file.isFile) { "The selected MIDI song is missing from the offline library." }
            val font = soundFont?.takeIf { it.isFile }
                ?: throw IllegalStateException("Install an SF2 SoundFont from Offline Library Setup before playing songs.")
            val parsed = KaraokeMidiParser.parse(file)
            val created = MidiFilePlayer(
                soundFontPath = font.absolutePath,
                midiPath = file.absolutePath,
                // Medium interpolation keeps CPU use down on entry-level phones while retaining clean playback.
                config = AudioConfig(sampleRate = 44100, interpolation = Interpolation.NORMAL)
            )
            player = created
            timeline = parsed
            mutableState.value = PlaybackState(file = file, durationMs = parsed.durationMs, lyrics = parsed.lyrics)
            startTicker()
        } catch (error: Throwable) {
            mutableState.value = PlaybackState(error = error.message ?: "Could not load this MIDI song.")
            throw error
        }
    }

    override fun play() {
        val active = player ?: return
        active.play {
            val previous = mutableState.value
            mutableState.value = previous.copy(playing = false, positionMs = previous.durationMs)
        }
        mutableState.value = mutableState.value.copy(playing = true, error = null)
    }

    override fun pause() {
        player?.pause()
        mutableState.value = mutableState.value.copy(playing = false)
    }

    override fun seek(positionMs: Long) {
        val active = player ?: return
        val duration = timeline?.durationMs ?: 0L
        val targetMs = positionMs.coerceIn(0L, duration)
        val ticks = active.durationTicks
        if (duration > 0L && ticks > 0L) active.seekTo((targetMs * ticks / duration).coerceIn(0L, ticks))
        mutableState.value = mutableState.value.copy(positionMs = targetMs)
    }

    override fun stop() {
        releasePlayer()
        mutableState.value = PlaybackState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val active = player
                val current = timeline
                if (active != null && current != null) {
                    val ticks = active.durationTicks
                    val position = if (ticks > 0L) current.durationMs * active.currentTick.coerceIn(0L, ticks) / ticks else 0L
                    mutableState.value = mutableState.value.copy(
                        playing = active.isPlaying,
                        positionMs = position.coerceAtMost(current.durationMs)
                    )
                }
                // Avoid forcing the entire Compose screen tree to recompose ten times a second.
                delay(200L)
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
        player?.let { active -> runCatching { active.stop() }; runCatching { active.close() } }
        player = null
        timeline = null
    }

    override fun release() {
        releasePlayer()
        scope.cancel()
    }
}

interface MidiEngine {
    val state: StateFlow<PlaybackState>
    suspend fun setSoundFont(file: File)
    suspend fun load(file: File)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun stop()
    fun release()
}
