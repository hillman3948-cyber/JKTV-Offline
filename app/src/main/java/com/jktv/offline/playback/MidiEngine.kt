package com.jktv.offline.playback

import dev.kotlinds.fluidsynthkmp.AudioConfig
import com.jktv.offline.audio.PcmSynth
import com.jktv.offline.audio.PcmOutputGuard
import dev.kotlinds.fluidsynthkmp.Interpolation
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.update
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AudioMixerConfig(
    val masterGain: Float = 0.85f,
    val melodyVolume: Float = 1.0f,
    val drumsVolume: Float = 1.0f,
    val bassVolume: Float = 1.0f,
    val accompanimentVolume: Float = 1.0f,
    val reverbLevel: Float = 0.45f,
    val reverbRoomSize: Float = 0.60f,
    val chorusLevel: Float = 0.25f,
    val highQualityInterpolation: Boolean = true
)

data class AudioOutputDiagnostics(
    val sampleRate: Int = 44100, val underruns: Int = 0,
    val peakBeforeProtection: Float = 0f, val peakAfterProtection: Float = 0f,
    val limitedBlocks: Long = 0L, val invalidSamples: Long = 0L
)

data class PlaybackState(
    val transportRevision: Long = 0L,
    val file: File? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<TimedLyricEvent> = emptyList(),
    val lyricLines: List<LyricLine> = emptyList(),
    val melodyNotes: List<MelodyNote> = emptyList(),
    val melodyChannel: Int = 0,
    val bpm: Int = 120,
    val tempoMultiplier: Float = 1.0f,
    val clockAnchorWallNs: Long = 0L,
    val clockAnchorSongUs: Long = 0L,
    val audioBufferLatencyMs: Long = 23L,
    val effectiveLatencyOffsetMs: Long = 0L,
    val syncSourceType: SyncSourceType = SyncSourceType.NO_LYRICS,
    val syncConfidence: SyncConfidence = SyncConfidence.UNSYNCHRONIZED,
    val interludeSkipTargetMs: Long? = null,
    val channelLevels: List<Float> = List(16) { 0f },
    val completedId: Long = 0L,
    val error: String? = null
) {
    /**
     * Computes the exact, frame-locked synchronized lyric position in milliseconds from the single
     * authoritative monotonic MIDI clock, accounting for audio output buffer latency and user calibration.
     */
    fun syncedLyricPositionMs(nowWallNs: Long = System.nanoTime()): Long {
        if (durationMs <= 0L) return 0L
        val rawSongMs = if (playing && clockAnchorWallNs > 0L) {
            val deltaWallNs = (nowWallNs - clockAnchorWallNs).coerceIn(0L, 80_000_000L)
            val deltaSongUs = ((deltaWallNs / 1000.0) * tempoMultiplier.coerceIn(0.5f, 1.5f)).toLong()
            (clockAnchorSongUs + deltaSongUs) / 1000L
        } else {
            positionMs
        }
        val compensated = rawSongMs - audioBufferLatencyMs + effectiveLatencyOffsetMs
        return compensated.coerceIn(0L, durationMs)
    }
}

/**
 * High-precision Android FluidSynth Karaoke Engine using a persistent SF2 synthesizer context
 * and single authoritative monotonic Kotlin MIDI sequencer clock for zero-drift lyrics synchronization,
 * real-time key transpose, tempo scaling, melody guide muting, and per-group instrument mixing.
 */
class FluidSynthMidiEngine : MidiEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transportRevision = java.util.concurrent.atomic.AtomicLong()
    private val pcmDispatcher=java.util.concurrent.Executors.newSingleThreadExecutor {task->Thread(task,"JKTV-PCM")}.asCoroutineDispatcher()
    private val synthLock = Any()
    private val loadMutex = Mutex()
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private var soundFont: File? = null
    val activeSoundFont: File? get() = soundFont
    @Volatile private var outputMuted = false

    /** Keep the authoritative sequencer running while routing audible output to the TV. */
    fun setOutputMuted(muted: Boolean) {
        outputMuted = muted
        runCatching { outputTrack?.setVolume(if (muted) 0f else 1f) }
    }
    fun isOutputMuted(): Boolean = outputMuted
    @Volatile var pcmSink: ((Long, Double, Double, Float, Boolean, ByteArray) -> Unit)? = null
    @Volatile var audioDiagnostics = AudioOutputDiagnostics()
        private set
    @Volatile private var outputTrack: android.media.AudioTrack? = null
    private var pcmJob: Job? = null
    @Volatile private var released = false
    @Volatile private var requestedPresentationLatency = 0L
    @Volatile private var localLatency = 23L
    fun setAudioPresentationLatencyMs(value: Long) {
        requestedPresentationLatency = value
        audioBufferLatencyMs = if (value > 0) value else localLatency
        mutableState.update { it.copy(audioBufferLatencyMs = audioBufferLatencyMs) }
    }

    @Synchronized private fun startPcmOutput() {
        if (pcmJob?.isActive == true || released) return
        pcmJob = scope.launch(pcmDispatcher) {
            var track: android.media.AudioTrack? = null
            val outputGuard = PcmOutputGuard()
            var diagnosticBlocks = 0
            try {
                runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) }
                val minimum = android.media.AudioTrack.getMinBufferSize(44100,
                    android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_FLOAT)
                require(minimum > 0) { "Stereo PCM output is unavailable" }
                track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(44100)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT).build())
                    .setBufferSizeInBytes(maxOf(minimum,256*4*2*4))
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM).build()
                require(track.state == android.media.AudioTrack.STATE_INITIALIZED)
                outputTrack = track
                localLatency = (track.bufferSizeInFrames*1000L/44100 + kotlin.math.ceil(outputGuard.latencyMs).toLong()).coerceAtLeast(10)
                if (requestedPresentationLatency == 0L) setAudioPresentationLatencyMs(0)
                track.setVolume(if (outputMuted) 0f else 1f)
                track.play()
                while (isActive && !released) {
                    var rawPosition = 0.0
                    var captured = 0.0
                    var revision = 0L
                    var playing = false
                    var tempo = 1f
                    val rendered = synchronized(synthLock) {
                        val player = synth ?: return@synchronized null
                        captured = System.nanoTime()/1_000_000.0
                        rawPosition = songPositionUs/1000.0 + if (isPlayingFlag)
                            ((System.nanoTime()-clockAnchorWallNs).coerceIn(0L,80_000_000L)/1_000_000.0)*tempoMultiplier else 0.0
                        revision = transportRevision.get()
                        playing = isPlayingFlag
                        tempo = tempoMultiplier
                        player.renderFloat(256)
                    }
                    if (rendered == null) { delay(10); continue }
                    val samples = outputGuard.process(rendered, revision)
                    rawPosition = (rawPosition - if (playing) outputGuard.latencyMs * tempo else 0.0).coerceAtLeast(0.0)
                    captured -= outputGuard.latencyMs
                    if (++diagnosticBlocks >= 172) {
                        diagnosticBlocks = 0
                        audioDiagnostics = AudioOutputDiagnostics(underruns = track.underrunCount,
                            peakBeforeProtection = outputGuard.peakBefore, peakAfterProtection = outputGuard.peakAfter,
                            limitedBlocks = outputGuard.limitedBlocks, invalidSamples = outputGuard.invalidSamples)
                    }
                    val sink = pcmSink
                    if (sink != null) {
                        val bytes = ByteArray(samples.size*2)
                        samples.forEachIndexed { i,value ->
                            val n = (value.coerceIn(-1f,1f)*32767f).toInt()
                            bytes[i*2]=n.toByte();bytes[i*2+1]=(n shr 8).toByte()
                        }
                        sink(revision,rawPosition,captured,tempo,playing,bytes)
                    }
                    var written = 0
                    while (written < samples.size && isActive && !released) {
                        val count = track.write(samples,written,samples.size-written,android.media.AudioTrack.WRITE_BLOCKING)
                        check(count > 0) { "Audio output write failed ($count)" }
                        written += count
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (!released) mutableState.value=mutableState.value.copy(error=error.message ?: "PCM audio output failed")
            } finally {
                runCatching { track?.pause() };runCatching { track?.flush() };runCatching { track?.release() }
                if (outputTrack === track) outputTrack = null
            }
        }
    }
    private var loadedSoundFontPath: String? = null
    private var synth: PcmSynth? = null
    private var timeline: MidiTimeline? = null
    private var sourceResolved: ResolvedLyricsSync? = null
    private var phraseGrouping = "RAW_MIDI"
    fun setPhraseGrouping(value: String) { phraseGrouping = value; sourceResolved?.let { applyResolvedLyrics(it) } }
    private var sequencerJob: Job? = null

    @Volatile private var isPlayingFlag: Boolean = false
    @Volatile private var songPositionUs: Long = 0L
    @Volatile private var clockAnchorWallNs: Long = 0L
    @Volatile private var eventCursor: Int = 0
    @Volatile private var transposeSemitones: Int = 0
    @Volatile private var tempoMultiplier: Float = 1.0f
    @Volatile private var melodyEnabled: Boolean = true
    @Volatile private var effectiveLatencyOffsetMs: Long = 0L
    @Volatile private var mixerConfig: AudioMixerConfig = AudioMixerConfig()

    // Hardware audio driver period latency: 256 frames * 4 periods / 44100 Hz ≈ 23 ms
    @Volatile private var audioBufferLatencyMs: Long = (256L * 4L * 1000L) / 44100L

    // Tracks active sounding keys per channel (channel -> rawKey -> playedTransposedKey)
    private val activeNotes = Array(16) { IntArray(128) { -1 } }
    private val channelVolumes = MidiChannelVolumes()
    private val channelLevelsInternal = FloatArray(16) { 0f }

    override val currentTimeline: MidiTimeline?
        get() = timeline

    override suspend fun setSoundFont(file: File) {
        require(file.isFile && file.length() > 0L) { "The installed SoundFont is missing or empty." }
        loadMutex.withLock {
            soundFont = file
            if (loadedSoundFontPath != null && loadedSoundFontPath != file.absolutePath) {
                rebuildSynthLocked(file)
            }
        }
    }

    private fun ensureSynthLocked(): PcmSynth {
        val font = soundFont?.takeIf { it.isFile }
            ?: throw IllegalStateException("Install an SF2 SoundFont from Offline Library Setup before playing songs.")
        val currentSynth = synth
        if (currentSynth != null && loadedSoundFontPath == font.absolutePath) {
            return currentSynth
        }
        return rebuildSynthLocked(font)
    }

    private fun rebuildSynthLocked(font: File): PcmSynth {
        synchronized(synthLock) {
            synth?.let { old ->
                silenceAllNotesLocked(old)
                runCatching { old.close() }
            }
            val created = PcmSynth(
                AudioConfig(
                    sampleRate = 44100,
                    periodSize = 256,
                    periods = 4,
                    interpolation = if (mixerConfig.highQualityInterpolation) Interpolation.HIGH else Interpolation.FAST
                )
            )
            val sfId = created.loadSoundFont(font.absolutePath)
            require(sfId >= 0) { "FluidSynth could not load SoundFont: ${font.name}" }
            applySynthEffectsLocked(created, mixerConfig)
            synth = created
            startPcmOutput()
            loadedSoundFontPath = font.absolutePath
            return created
        }
    }

    private fun applySynthEffectsLocked(player: PcmSynth, config: AudioMixerConfig) {
        runCatching {
            player.setGain(PcmOutputGuard.synthGain(config.masterGain))
            player.setReverb(
                config.reverbRoomSize.toDouble().coerceIn(0.0, 1.0),
                0.25,
                0.75,
                config.reverbLevel.toDouble().coerceIn(0.0, 1.0)
            )
            player.setChorus(
                3,
                config.chorusLevel.toDouble().coerceIn(0.0, 1.0),
                0.35,
                4.5
            )
            player.setInterpolation(
                if (config.highQualityInterpolation) Interpolation.HIGH else Interpolation.FAST
            )
        }
    }

    override suspend fun load(file: File) {
        stopInstrumentSample()
        sourceResolved = null
        loadMutex.withLock {
            stopSequencerInternal()
            try {
                require(file.isFile) { "The selected MIDI song is missing from the offline library." }
                val activeSynth = ensureSynthLocked()
                val parsed = KaraokeMidiParser.parse(file)
                synchronized(synthLock) {
                    silenceAllNotesLocked(activeSynth)
                    activeSynth.resetMidi()
                    channelVolumes.reset()
                    for (ch in 0..15) {
                        applyChannelMixLocked(activeSynth, parsed, ch)
                        channelLevelsInternal[ch] = 0f
                    }
                }
                timeline = parsed
                songPositionUs = 0L
                clockAnchorWallNs = System.nanoTime()
                eventCursor = 0
                val initialSyncType = when {
                    parsed.lyricLines.isNotEmpty() && parsed.hasEventTiming -> SyncSourceType.EMBEDDED_MIDI_KAR
                    parsed.lyrics.isNotEmpty() -> SyncSourceType.UNTIMED_TEXT
                    else -> SyncSourceType.NO_LYRICS
                }
                val initialConfidence = if (initialSyncType == SyncSourceType.EMBEDDED_MIDI_KAR) {
                    SyncConfidence.ACCURATE_SYLLABLE
                } else {
                    SyncConfidence.UNSYNCHRONIZED
                }
                mutableState.value = PlaybackState(
                    transportRevision = transportRevision.incrementAndGet(),
                    file = file,
                    playing = false,
                    positionMs = 0L,
                    durationMs = parsed.durationMs,
                    lyrics = parsed.lyrics,
                    lyricLines = parsed.lyricLines,
                    melodyNotes = parsed.melodyNotes,
                    melodyChannel = parsed.melodyChannel,
                    bpm = (parsed.bpm * tempoMultiplier).roundToInt().coerceIn(30, 300),
                    tempoMultiplier = tempoMultiplier,
                    clockAnchorWallNs = clockAnchorWallNs,
                    clockAnchorSongUs = 0L,
                    audioBufferLatencyMs = audioBufferLatencyMs,
                    effectiveLatencyOffsetMs = effectiveLatencyOffsetMs,
                    syncSourceType = initialSyncType,
                    syncConfidence = initialConfidence,
                    interludeSkipTargetMs = parsed.interludeSkipTargetMs(0L),
                    channelLevels = List(16) { 0f },
                    completedId = mutableState.value.completedId,
                    error = null
                )
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(
                    playing = false,
                    error = error.message ?: "Could not load this MIDI song."
                )
                throw error
            }
        }
    }

    override fun applyResolvedLyrics(resolved: ResolvedLyricsSync) {
        sourceResolved = resolved
        val presented = com.jktv.offline.stage.StageTimeline.presentationLines(resolved.lyricLines, phraseGrouping)
        val currentTl = timeline
        if (currentTl != null) {
            timeline = currentTl.copy(
                lyrics = resolved.flatEvents,
                lyricLines = presented,
                hasLyrics = presented.isNotEmpty(),
                hasEventTiming = resolved.confidence != SyncConfidence.UNSYNCHRONIZED
            )
        }
        val currentPos = mutableState.value.positionMs
        mutableState.value = mutableState.value.copy(
            lyrics = resolved.flatEvents,
            lyricLines = presented,
            syncSourceType = resolved.sourceType,
            syncConfidence = resolved.confidence,
            interludeSkipTargetMs = timeline?.interludeSkipTargetMs(currentPos)
        )
    }

    override fun setLatencyOffsetMs(offsetMs: Long) {
        effectiveLatencyOffsetMs = offsetMs.coerceIn(-5000L, 5000L)
        mutableState.value = mutableState.value.copy(
            effectiveLatencyOffsetMs = effectiveLatencyOffsetMs
        )
    }

    override fun play() {
        stopInstrumentSample()
        startPcmOutput()
        val currentTimeline = timeline ?: return
        if (synth == null) return
        if (songPositionUs >= currentTimeline.durationMs * 1000L && currentTimeline.durationMs > 0L) {
            seek(0L)
        }
        val nowNs = System.nanoTime()
        clockAnchorWallNs = nowNs
        isPlayingFlag = true
        mutableState.value = mutableState.value.copy(
            transportRevision = transportRevision.incrementAndGet(),
            playing = true,
            clockAnchorWallNs = nowNs,
            clockAnchorSongUs = songPositionUs,
            error = null
        )
        if (sequencerJob?.isActive == true) return

        sequencerJob = scope.launch {
            var lastWallNs = System.nanoTime()
            var lastUiEmitMs = 0L
            while (isActive) {
                val loopNowNs = System.nanoTime()
                val deltaNs = (loopNowNs - lastWallNs).coerceIn(0L, 100_000_000L)
                lastWallNs = loopNowNs

                val tl = timeline
                val player = synth
                if (tl == null || player == null) {
                    delay(15L)
                    continue
                }

                if (isPlayingFlag) {
                    val activeTempo = tempoMultiplier.coerceIn(0.5f, 1.5f)
                    val scaledDeltaUs = ((deltaNs / 1000.0) * activeTempo).toLong()
                    songPositionUs = (songPositionUs + scaledDeltaUs).coerceAtMost(tl.durationMs * 1000L)
                    clockAnchorWallNs = loopNowNs
                    val events = tl.midiEvents

                    synchronized(synthLock) {
                        while (eventCursor < events.size && events[eventCursor].timeUs <= songPositionUs) {
                            dispatchEventLocked(player, tl, events[eventCursor])
                            eventCursor++
                        }
                        for (ch in 0..15) {
                            channelLevelsInternal[ch] = (channelLevelsInternal[ch] * 0.90f).coerceAtLeast(0f)
                        }
                    }

                    val currentMs = songPositionUs / 1000L
                    if (currentMs >= tl.durationMs && tl.durationMs > 0L) {
                        synchronized(synthLock) {
                            silenceAllNotesLocked(player)
                        }
                        isPlayingFlag = false
                        val nextCompletedId = mutableState.value.completedId + 1L
                        mutableState.value = mutableState.value.copy(
                            playing = false,
                            positionMs = tl.durationMs,
                            clockAnchorWallNs = loopNowNs,
                            clockAnchorSongUs = songPositionUs,
                            interludeSkipTargetMs = null,
                            channelLevels = List(16) { 0f },
                            completedId = nextCompletedId
                        )
                        break
                    }

                    val nowMs = loopNowNs / 1_000_000L
                    if (nowMs - lastUiEmitMs >= 24L) {
                        lastUiEmitMs = nowMs
                        val syncedMs = ((songPositionUs / 1000L) - audioBufferLatencyMs + effectiveLatencyOffsetMs)
                            .coerceIn(0L, tl.durationMs)
                        mutableState.value = mutableState.value.copy(
                            playing = true,
                            positionMs = currentMs,
                            bpm = (tl.bpm * activeTempo).roundToInt().coerceIn(30, 300),
                            tempoMultiplier = activeTempo,
                            clockAnchorWallNs = loopNowNs,
                            clockAnchorSongUs = songPositionUs,
                            effectiveLatencyOffsetMs = effectiveLatencyOffsetMs,
                            interludeSkipTargetMs = tl.interludeSkipTargetMs(syncedMs),
                            channelLevels = channelLevelsInternal.toList()
                        )
                    }
                    delay(3L)
                } else {
                    delay(20L)
                }
            }
        }
    }

    private fun dispatchEventLocked(player: PcmSynth, tl: MidiTimeline, event: SequencerMidiEvent) {
        val ch = event.channel.coerceIn(0, 15)
        when (event.type) {
            MidiEventType.CONTROL_CHANGE -> {
                if (event.data1 == 7 || event.data1 == 39) {
                    channelVolumes.control(ch, event.data1, event.data2)
                    applyChannelMixLocked(player, tl, ch)
                } else {
                    player.controlChange(ch, event.data1, event.data2)
                    if (event.data1 == 121) applyChannelMixLocked(player, tl, ch)
                }
            }
            MidiEventType.PITCH_BEND -> player.pitchBend(ch, event.data1 or (event.data2 shl 7))
            MidiEventType.CHANNEL_PRESSURE -> player.channelPressure(ch, event.data1)
            MidiEventType.KEY_PRESSURE -> {
                val key = if (ch == 9) event.data1 else (event.data1 + transposeSemitones).coerceIn(0, 127)
                player.keyPressure(ch, key, event.data2)
            }
            MidiEventType.PROGRAM_CHANGE -> {
                runCatching { player.programChange(ch, event.data1.coerceIn(0, 127)) }
            }
            MidiEventType.NOTE_ON -> {
                val rawKey = event.data1.coerceIn(0, 127)
                val previousPlayedKey = activeNotes[ch][rawKey]
                if (previousPlayedKey >= 0) {
                    runCatching { player.noteOff(ch, previousPlayedKey) }
                    activeNotes[ch][rawKey] = -1
                }
                if (ch == tl.melodyChannel && !melodyEnabled) {
                    return
                }
                val scaledVelocity = event.data2.coerceIn(0, 127)
                if (scaledVelocity <= 0) return

                val playedKey = if (ch == 9) {
                    rawKey
                } else {
                    (rawKey + transposeSemitones).coerceIn(0, 127)
                }
                activeNotes[ch][rawKey] = playedKey
                channelLevelsInternal[ch] = maxOf(channelLevelsInternal[ch], scaledVelocity / 127f)
                runCatching { player.noteOn(ch, playedKey, scaledVelocity) }
            }
            MidiEventType.NOTE_OFF -> {
                val rawKey = event.data1.coerceIn(0, 127)
                val playedKey = activeNotes[ch][rawKey]
                if (playedKey >= 0) {
                    activeNotes[ch][rawKey] = -1
                    runCatching { player.noteOff(ch, playedKey) }
                } else {
                    val fallbackKey = if (ch == 9) rawKey else (rawKey + transposeSemitones).coerceIn(0, 127)
                    runCatching { player.noteOff(ch, fallbackKey) }
                }
            }
        }
    }

    private fun applyChannelMixLocked(player: PcmSynth, tl: MidiTimeline, ch: Int) {
        val group = when {
            ch == tl.melodyChannel -> mixerConfig.melodyVolume
            ch == 9 -> mixerConfig.drumsVolume
            ch in tl.bassChannels -> mixerConfig.bassVolume
            else -> mixerConfig.accompanimentVolume
        }
        val volume = channelVolumes.scaled(ch, group)
        player.controlChange(ch, 7, volume shr 7)
        player.controlChange(ch, 39, volume and 127)
    }

    override fun pause() {
        isPlayingFlag = false
        val player = synth
        if (player != null) {
            synchronized(synthLock) {
                silenceAllNotesLocked(player)
            }
        }
        val nowNs = System.nanoTime()
        clockAnchorWallNs = nowNs
        mutableState.value = mutableState.value.copy(
            transportRevision = transportRevision.incrementAndGet(),
            playing = false,
            positionMs = songPositionUs / 1000L,
            clockAnchorWallNs = nowNs,
            clockAnchorSongUs = songPositionUs,
            channelLevels = List(16) { 0f }
        )
    }

    override fun seek(positionMs: Long) {
        val tl = timeline ?: return
        val player = synth ?: return
        val targetMs = positionMs.coerceIn(0L, tl.durationMs)
        val targetUs = targetMs * 1000L
        val events = tl.midiEvents

        synchronized(synthLock) {
            silenceAllNotesLocked(player)
            player.resetMidi()
            channelVolumes.reset()
            for (ch in 0..15) applyChannelMixLocked(player, tl, ch)
            var newCursor = 0
            while (newCursor < events.size && events[newCursor].timeUs < targetUs) {
                val ev = events[newCursor]
                if (ev.type != MidiEventType.NOTE_ON && ev.type != MidiEventType.NOTE_OFF &&
                    ev.type != MidiEventType.KEY_PRESSURE) {
                    dispatchEventLocked(player, tl, ev)
                }
                newCursor++
            }
            for (ch in 0..15) {
                channelLevelsInternal[ch] = 0f
            }
            eventCursor = newCursor
            songPositionUs = targetUs
            clockAnchorWallNs = System.nanoTime()
        }

        mutableState.value = mutableState.value.copy(
            transportRevision = transportRevision.incrementAndGet(),
            positionMs = targetMs,
            clockAnchorWallNs = clockAnchorWallNs,
            clockAnchorSongUs = targetUs,
            interludeSkipTargetMs = tl.interludeSkipTargetMs(targetMs)
        )
    }

    override fun setTranspose(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        if (transposeSemitones == clamped) return
        val player = synth
        synchronized(synthLock) {
            transposeSemitones = clamped
            if (player != null) {
                for (ch in 0..15) {
                    if (ch == 9) continue
                    player.allSoundsOff(ch) // Also stop notes held by the song's sustain pedal.
                    for (rawKey in 0..127) {
                        val played = activeNotes[ch][rawKey]
                        if (played >= 0) {
                            runCatching { player.noteOff(ch, played) }
                            activeNotes[ch][rawKey] = -1
                        }
                    }
                }
            }
        }
    }

    override fun setTempoMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.5f, 1.5f)
        val nowNs = System.nanoTime()
        clockAnchorWallNs = nowNs
        tempoMultiplier = clamped
        val tl = timeline
        if (tl != null) {
            mutableState.value = mutableState.value.copy(
                transportRevision = transportRevision.incrementAndGet(),
                bpm = (tl.bpm * clamped).roundToInt().coerceIn(30, 300),
                tempoMultiplier = clamped,
                clockAnchorWallNs = nowNs,
                clockAnchorSongUs = songPositionUs
            )
        }
    }

    override fun setMelodyEnabled(enabled: Boolean) {
        if (melodyEnabled == enabled) return
        melodyEnabled = enabled
        val player = synth
        val tl = timeline
        if (!enabled && player != null && tl != null) {
            synchronized(synthLock) {
                val ch = tl.melodyChannel.coerceIn(0, 15)
                player.allSoundsOff(ch)
                for (rawKey in 0..127) {
                    val played = activeNotes[ch][rawKey]
                    if (played >= 0) {
                        runCatching { player.noteOff(ch, played) }
                        activeNotes[ch][rawKey] = -1
                    }
                }
                channelLevelsInternal[ch] = 0f
            }
        }
    }

    override fun setMixerConfig(config: AudioMixerConfig) {
        mixerConfig = config
        val player = synth
        if (player != null) {
            synchronized(synthLock) {
                applySynthEffectsLocked(player, config)
                timeline?.let { tl -> for (ch in 0..15) applyChannelMixLocked(player, tl, ch) }
            }
        }
    }

    override fun stop() {
        stopSequencerInternal()
        timeline = null
        mutableState.value = PlaybackState(transportRevision = transportRevision.incrementAndGet(), completedId = mutableState.value.completedId)
    }

    private fun stopSequencerInternal() {
        isPlayingFlag = false
        sequencerJob?.cancel()
        sequencerJob = null
        val player = synth
        if (player != null) {
            synchronized(synthLock) {
                silenceAllNotesLocked(player)
            }
        }
    }

    private fun silenceAllNotesLocked(player: PcmSynth) {
        for (ch in 0..15) {
            player.allSoundsOff(ch) // Includes pedal-held voices no longer in activeNotes.
            for (rawKey in 0..127) {
                val played = activeNotes[ch][rawKey]
                if (played >= 0) {
                    runCatching { player.noteOff(ch, played) }
                    activeNotes[ch][rawKey] = -1
                }
            }
        }
    }

    override fun release() {
        released = true
        pcmSink = null
        pcmJob?.cancel()
        runCatching { outputTrack?.pause() };runCatching { outputTrack?.flush() };runCatching { outputTrack?.release() }
        outputTrack = null
        stopSequencerInternal()
        synchronized(synthLock) {
            synth?.let { runCatching { it.close() } }
            synth = null
            loadedSoundFontPath = null
        }
        scope.cancel()
        pcmDispatcher.close()
    }

    private var previewJob: Job? = null

    override suspend fun playInstrumentSample(instrument: String, soundFontFile: File?) {
        if (isPlayingFlag) return // Preview must not change programs or overlap the karaoke mix.
        stopInstrumentSample()
        val font = soundFontFile ?: soundFont?.takeIf { it.isFile } ?: return
        loadMutex.withLock {
            if (soundFont?.absolutePath != font.absolutePath) {
                soundFont = font
                rebuildSynthLocked(font)
            }
        }
        val player = ensureSynthLocked()
        previewJob = scope.launch {
            val ch = if (instrument.equals("DRUMS", ignoreCase = true)) 9 else 0
            val program = when (instrument.uppercase()) {
                "PIANO" -> 0
                "GUITAR" -> 24
                "STRINGS" -> 48
                "BRASS" -> 61
                "DRUMS" -> 0
                else -> 0
            }

            synchronized(synthLock) {
                runCatching {
                    player.programChange(ch, program)
                }
            }

            when (instrument.uppercase()) {
                "PIANO" -> {
                    val notes = listOf(60 to 280L, 64 to 280L, 67 to 280L, 72 to 550L, 67 to 300L, 72 to 700L)
                    for ((note, dur) in notes) {
                        if (!isActive) break
                        synchronized(synthLock) { runCatching { player.noteOn(ch, note, 95) } }
                        delay(dur)
                        synchronized(synthLock) { runCatching { player.noteOff(ch, note) } }
                        delay(40L)
                    }
                }
                "GUITAR" -> {
                    val notes = listOf(52 to 240L, 59 to 240L, 64 to 240L, 68 to 240L, 71 to 240L, 76 to 800L)
                    for ((note, dur) in notes) {
                        if (!isActive) break
                        synchronized(synthLock) { runCatching { player.noteOn(ch, note, 90) } }
                        delay(dur)
                        synchronized(synthLock) { runCatching { player.noteOff(ch, note) } }
                        delay(35L)
                    }
                }
                "STRINGS" -> {
                    val chord = listOf(60, 67, 76)
                    synchronized(synthLock) {
                        for (note in chord) runCatching { player.noteOn(ch, note, 85) }
                    }
                    delay(1800L)
                    synchronized(synthLock) {
                        for (note in chord) runCatching { player.noteOff(ch, note) }
                    }
                }
                "BRASS" -> {
                    val notes = listOf(60 to 220L, 64 to 220L, 67 to 220L, 72 to 750L)
                    for ((note, dur) in notes) {
                        if (!isActive) break
                        synchronized(synthLock) { runCatching { player.noteOn(ch, note, 105) } }
                        delay(dur)
                        synchronized(synthLock) { runCatching { player.noteOff(ch, note) } }
                        delay(50L)
                    }
                }
                "DRUMS" -> {
                    val hits = listOf(
                        listOf(36, 42) to 250L,
                        listOf(42) to 250L,
                        listOf(38, 42) to 250L,
                        listOf(42) to 250L,
                        listOf(36, 42) to 250L,
                        listOf(36, 42) to 250L,
                        listOf(38, 49) to 700L
                    )
                    for ((hitKeys, dur) in hits) {
                        if (!isActive) break
                        synchronized(synthLock) {
                            for (k in hitKeys) runCatching { player.noteOn(9, k, 100) }
                        }
                        delay(dur)
                        synchronized(synthLock) {
                            for (k in hitKeys) runCatching { player.noteOff(9, k) }
                        }
                    }
                }
            }
        }
    }

    override fun stopInstrumentSample() {
        if (previewJob == null) return
        previewJob?.cancel()
        previewJob = null
        val player = synth
        if (player != null) {
            synchronized(synthLock) {
                runCatching {
                    for (ch in listOf(0, 9)) {
                        for (key in 0..127) {
                            player.noteOff(ch, key)
                        }
                    }
                }
            }
        }
    }
}

interface MidiEngine {
    val state: StateFlow<PlaybackState>
    val currentTimeline: MidiTimeline?
    suspend fun setSoundFont(file: File)
    suspend fun load(file: File)
    fun applyResolvedLyrics(resolved: ResolvedLyricsSync)
    fun setLatencyOffsetMs(offsetMs: Long)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun setTranspose(semitones: Int)
    fun setTempoMultiplier(multiplier: Float)
    fun setMelodyEnabled(enabled: Boolean)
    fun setMixerConfig(config: AudioMixerConfig)
    suspend fun playInstrumentSample(instrument: String, soundFontFile: File? = null)
    fun stopInstrumentSample()
    fun stop()
    fun release()
}
