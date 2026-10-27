package com.jktv.offline.scoring

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.jktv.offline.playback.MelodyNote
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
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

data class LiveScoreState(
    val active: Boolean = false,
    val permissionGranted: Boolean = false,
    val micLevel: Float = 0f,
    val detectedHz: Float = 0f,
    val detectedMidiNote: Int = -1,
    val targetMidiNote: Int = -1,
    val semitoneOffset: Float = 0f,
    val currentScore: Int = 85,
    val pitchScore: Int = 85,
    val timingScore: Int = 85,
    val stabilityScore: Int = 85,
    val comboStreak: Int = 0,
    val maxStreak: Int = 0,
    val hitNotes: Int = 0,
    val totalEvaluated: Int = 0,
    val feedbackText: String = "READY"
)

data class FinalScoreCard(
    val songTitle: String,
    val singerName: String,
    val totalScore: Int,
    val pitchScore: Int,
    val timingScore: Int,
    val stabilityScore: Int,
    val maxStreak: Int,
    val badge: String,
    val compliment: String
)

/**
 * Real-time YIN Autocorrelation Microphone Pitch Detector and Karaoke Scorer.
 * Compares live vocal fundamental frequency (Hz -> MIDI note) against the song's
 * transposed vocal melody notes using octave-folded semitone distance.
 */
class KaraokeScorer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(LiveScoreState(permissionGranted = hasMicPermission()))
    val state: StateFlow<LiveScoreState> = mutableState.asStateFlow()

    private var recordJob: Job? = null
    @Volatile private var melodyTimeline: List<MelodyNote> = emptyList()
    @Volatile private var currentPositionMs: Long = 0L
    @Volatile private var currentTranspose: Int = 0
    @Volatile private var isSongPlaying: Boolean = false

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophoneHardware(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    fun setSongTimeline(notes: List<MelodyNote>) {
        melodyTimeline = notes
        resetSessionCounters()
    }

    fun updatePlaybackContext(positionMs: Long, transpose: Int, playing: Boolean) {
        currentPositionMs = positionMs
        currentTranspose = transpose
        isSongPlaying = playing
    }

    fun resetSessionCounters() {
        val prev = mutableState.value
        mutableState.value = LiveScoreState(
            active = prev.active,
            permissionGranted = hasMicPermission()
        )
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasMicPermission() || !hasMicrophoneHardware()) {
            mutableState.value = mutableState.value.copy(
                active = false,
                permissionGranted = hasMicPermission(),
                feedbackText = "MIC PERMISSION NEEDED"
            )
            return
        }
        if (recordJob?.isActive == true) return
        resetSessionCounters()

        recordJob = scope.launch {
            val sampleRate = 16000
            val frameSize = 1024
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameSize * 2)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder?.release() }
                mutableState.value = mutableState.value.copy(
                    active = false,
                    feedbackText = "MIC UNAVAILABLE"
                )
                return@launch
            }

            val shortBuf = ShortArray(frameSize)
            val floatBuf = FloatArray(frameSize)
            var pitchSum = 0.0
            var timingSum = 0.0
            var stabilitySum = 0.0
            var evaluated = 0
            var hits = 0
            var streak = 0
            var maxStreak = 0
            var lastDetectedMidi = -1f

            try {
                recorder.startRecording()
                mutableState.value = mutableState.value.copy(
                    active = true,
                    permissionGranted = true,
                    feedbackText = "LISTENING"
                )

                while (isActive) {
                    if (!isSongPlaying) {
                        delay(80L)
                        continue
                    }
                    val read = recorder.read(shortBuf, 0, frameSize)
                    if (read <= 0) {
                        delay(20L)
                        continue
                    }

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = shortBuf[i] / 32768.0f
                        floatBuf[i] = s
                        sumSq += (s * s)
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    val level = (rms * 5.5f).coerceIn(0f, 1f)

                    val posMs = currentPositionMs
                    val targetRaw = findTargetMelodyNote(posMs)
                    val targetMidi = if (targetRaw >= 0) (targetRaw + currentTranspose).coerceIn(0, 127) else -1

                    val detectedHz = if (rms >= 0.012f) detectYinPitch(floatBuf, read, sampleRate) else -1f
                    val detectedMidiFloat = if (detectedHz in 75f..1100f) hzToMidi(detectedHz) else -1f
                    val detectedMidiInt = if (detectedMidiFloat > 0f) detectedMidiFloat.roundToInt() else -1

                    var semitoneOffset = 0f
                    var feedback = if (targetMidi >= 0) "SING NOW" else "INSTRUMENTAL"

                    if (targetMidi >= 0) {
                        evaluated++
                        if (detectedMidiFloat > 0f) {
                            // Octave-invariant semitone distance (-6..+6)
                            var diff = (detectedMidiFloat - targetMidi) % 12f
                            if (diff > 6f) diff -= 12f
                            if (diff < -6f) diff += 12f
                            semitoneOffset = diff.coerceIn(-6f, 6f)

                            val absDiff = abs(diff)
                            val framePitchScore = when {
                                absDiff <= 0.85f -> 100.0
                                absDiff <= 1.65f -> 92.0
                                absDiff <= 2.50f -> 82.0
                                absDiff <= 3.50f -> 72.0
                                else -> 58.0
                            }
                            val frameStability = if (lastDetectedMidi > 0f) {
                                val jitter = abs(detectedMidiFloat - lastDetectedMidi)
                                if (jitter <= 1.2f) 98.0 else 78.0
                            } else 90.0

                            pitchSum += framePitchScore
                            timingSum += 96.0
                            stabilitySum += frameStability
                            lastDetectedMidi = detectedMidiFloat

                            if (absDiff <= 2.2f) {
                                hits++
                                streak++
                                if (streak > maxStreak) maxStreak = streak
                                feedback = if (absDiff <= 0.9f) "PERFECT! 🔥" else "GREAT! ✨"
                            } else {
                                streak = 0
                                feedback = if (diff > 0f) "A BIT HIGH" else "A BIT LOW"
                            }
                        } else {
                            // Target note exists but user hasn't sung this frame
                            pitchSum += 74.0
                            timingSum += 65.0
                            stabilitySum += 78.0
                            lastDetectedMidi = -1f
                        }
                    } else if (detectedMidiFloat > 0f) {
                        feedback = "WARMING UP 🎤"
                    }

                    val pScore = if (evaluated > 0) (pitchSum / evaluated).roundToInt().coerceIn(55, 100) else 85
                    val tScore = if (evaluated > 0) (timingSum / evaluated).roundToInt().coerceIn(55, 100) else 85
                    val sScore = if (evaluated > 0) (stabilitySum / evaluated).roundToInt().coerceIn(55, 100) else 85
                    val total = ((pScore * 0.50) + (tScore * 0.30) + (sScore * 0.20)).roundToInt().coerceIn(55, 100)

                    mutableState.value = LiveScoreState(
                        active = true,
                        permissionGranted = true,
                        micLevel = level,
                        detectedHz = if (detectedHz > 0f) detectedHz else 0f,
                        detectedMidiNote = detectedMidiInt,
                        targetMidiNote = targetMidi,
                        semitoneOffset = semitoneOffset,
                        currentScore = total,
                        pitchScore = pScore,
                        timingScore = tScore,
                        stabilityScore = sScore,
                        comboStreak = streak,
                        maxStreak = maxStreak,
                        hitNotes = hits,
                        totalEvaluated = evaluated,
                        feedbackText = feedback
                    )
                    delay(35L)
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                mutableState.value = mutableState.value.copy(
                    active = false,
                    micLevel = 0f
                )
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        mutableState.value = mutableState.value.copy(active = false, micLevel = 0f)
    }

    fun buildFinalScoreCard(songTitle: String, singerName: String): FinalScoreCard {
        val s = mutableState.value
        val finalTotal = if (s.totalEvaluated >= 10) s.currentScore else 86
        val (badge, compliment) = when {
            finalTotal >= 95 -> "S+ GOLDEN VOICE 🏆" to "Phenomenal pitch & stage presence! Concert ready!"
            finalTotal >= 88 -> "A+ STAR PERFORMER 🌟" to "Awesome vocal accuracy and smooth rhythm!"
            finalTotal >= 80 -> "A CROWD FAVORITE 🎉" to "Great performance! Keep the party rolling!"
            finalTotal >= 70 -> "B RISING SINGER 🎤" to "Good effort and energy! Practice makes perfect!"
            else -> "C ENTHUSIAST 🎶" to "Fun performance! Try adjusting the Key to match your vocal range!"
        }
        return FinalScoreCard(
            songTitle = songTitle,
            singerName = singerName.ifBlank { "Stage Singer" },
            totalScore = finalTotal,
            pitchScore = s.pitchScore,
            timingScore = s.timingScore,
            stabilityScore = s.stabilityScore,
            maxStreak = s.maxStreak,
            badge = badge,
            compliment = compliment
        )
    }

    /**
     * Plays a pleasant synthesized celebratory arpeggio fanfare when the score card appears.
     */
    fun playScoreFanfare(score: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val sampleRate = 22050
                val notesHz = if (score >= 88) {
                    floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f) // C5 - E5 - G5 - C6
                } else {
                    floatArrayOf(440.0f, 554.37f, 659.25f) // A4 - C#5 - E5
                }
                val durationSamples = (sampleRate * 0.85).toInt()
                val pcm = ShortArray(durationSamples)
                for (i in 0 until durationSamples) {
                    val t = i.toDouble() / sampleRate
                    val step = min((t / 0.16).toInt(), notesHz.lastIndex)
                    val freq = notesHz[step]
                    val env = (1.0 - (i.toDouble() / durationSamples)).coerceIn(0.0, 1.0)
                    val wave = (sin(2.0 * PI * freq * t) * 0.6 + sin(2.0 * PI * (freq * 1.5) * t) * 0.25) * env
                    pcm[i] = (wave * 14000).toInt().coerceIn(-32767, 32767).toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(950L)
                track.release()
            }
        }
    }

    private fun findTargetMelodyNote(posMs: Long): Int {
        val list = melodyTimeline
        if (list.isEmpty()) return -1
        var low = 0
        var high = list.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val note = list[mid]
            if (posMs in (note.startMs - 90L)..(note.endMs + 90L)) {
                return note.midiNote
            }
            if (note.endMs < posMs) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return -1
    }

    private fun detectYinPitch(buffer: FloatArray, length: Int, sampleRate: Int): Float {
        val half = min(length / 2, 512)
        if (half < 32) return -1f
        val yin = FloatArray(half)
        for (tau in 1 until half) {
            var sum = 0f
            for (i in 0 until half) {
                val delta = buffer[i] - buffer[i + tau]
                sum += delta * delta
            }
            yin[tau] = sum
        }
        yin[0] = 1f
        var runningSum = 0f
        for (tau in 1 until half) {
            runningSum += yin[tau]
            yin[tau] = if (runningSum > 0f) yin[tau] * tau / runningSum else 1f
        }
        val threshold = 0.18f
        var tauEstimate = -1
        var tau = 16 // ~1000 Hz max
        while (tau < half - 1) {
            if (yin[tau] < threshold) {
                while (tau + 1 < half && yin[tau + 1] < yin[tau]) {
                    tau++
                }
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate <= 0) return -1f

        val x0 = if (tauEstimate < 1) tauEstimate else tauEstimate - 1
        val x2 = if (tauEstimate + 1 < half) tauEstimate + 1 else tauEstimate
        val betterTau = when {
            x0 == tauEstimate -> if (yin[tauEstimate] <= yin[x2]) tauEstimate.toFloat() else x2.toFloat()
            x2 == tauEstimate -> if (yin[tauEstimate] <= yin[x0]) tauEstimate.toFloat() else x0.toFloat()
            else -> {
                val s0 = yin[x0]
                val s1 = yin[tauEstimate]
                val s2 = yin[x2]
                val denom = 2f * (2f * s1 - s2 - s0)
                if (abs(denom) > 1e-5f) tauEstimate + (s2 - s0) / denom else tauEstimate.toFloat()
            }
        }
        return if (betterTau > 0f) sampleRate / betterTau else -1f
    }

    private fun hzToMidi(hz: Float): Float =
        (69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))).toFloat()

    fun release() {
        stop()
        scope.cancel()
    }
}
