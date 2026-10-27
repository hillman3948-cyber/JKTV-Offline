package com.jktv.offline.scoring

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Offline voice announcer using Android TextToSpeech for automatic singer stage introductions,
 * duet callouts, score announcements, and song number keypad feedback.
 */
class VoiceAnnouncer(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = runCatching {
        TextToSpeech(context.applicationContext, this)
    }.getOrNull()
    @Volatile private var ready: Boolean = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            runCatching {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.02f)
                tts?.setPitch(1.04f)
                ready = true
            }
        }
    }

    fun announceNextSinger(
        singer: String,
        secondSinger: String,
        songTitle: String,
        mode: String
    ) {
        if (!ready) return
        val cleanSinger = singer.trim().ifBlank { "Stage Singer" }
        val cleanPartner = secondSinger.trim()
        val message = when {
            mode.equals("DUET", ignoreCase = true) && cleanPartner.isNotBlank() ->
                "Now on stage, a duet by $cleanSinger and $cleanPartner, singing $songTitle!"
            mode.equals("PARTY", ignoreCase = true) ->
                "Party mode! $cleanSinger is up next with $songTitle!"
            else ->
                "Now singing, $cleanSinger, with $songTitle!"
        }
        speak(message)
    }

    fun announceScore(singer: String, score: Int) {
        if (!ready) return
        val cleanSinger = singer.trim().ifBlank { "Singer" }
        val msg = if (score >= 90) {
            "Amazing job $cleanSinger! You scored $score points!"
        } else {
            "Great singing $cleanSinger! Your score is $score!"
        }
        speak(msg)
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jktv_${System.currentTimeMillis()}")
        }
    }

    fun stop() { runCatching { tts?.stop() } }

    fun release() {
        ready = false
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
