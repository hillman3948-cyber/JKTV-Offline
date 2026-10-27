package com.jktv.offline.cast

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.jktv.offline.LibraryUiState
import com.jktv.offline.playback.FluidSynthMidiEngine
import com.jktv.offline.playback.PlaybackState
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.abs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class TvStageState(
    val library: LibraryUiState = LibraryUiState(), val playback: PlaybackState = PlaybackState(),
    val title: String = "", val session: Boolean = false, val message: String = "Starting receiver…",
    val port: Int = 0
)

class TvReceiver(context: Context) {
    val pairingKey = CastWire.newSecret()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovery = CastDiscovery(context)
    private val engine = FluidSynthMidiEngine()
    private val mutableState = MutableStateFlow(TvStageState())
    val state = mutableState.asStateFlow()
    private val cache = File(context.filesDir, "tv-stage-cache").apply { mkdirs() }
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var tvAudio = false
    @Volatile private var lastTick = 0L
    @Volatile private var remotePlayback = PlaybackState()
    @Volatile private var closed = false
    private var loadedMidi = ""
    private var loadedFont = ""

    init {
        scope.launch {
            while (isActive) {
                val now = System.nanoTime()
                val elapsed = if (lastTick == 0L) 0L else (now - lastTick) / 1_000_000
                if (elapsed > StageClock.AUDIO_LEASE_MS && engine.state.value.playing) engine.pause()
                val playback = if (tvAudio) engine.state.value else {
                    val anchor = remotePlayback
                    val position = (anchor.positionMs + if (anchor.playing) (elapsed.coerceAtMost(2000) * anchor.tempoMultiplier).toLong() else 0L)
                        .coerceIn(0L, anchor.durationMs.coerceAtLeast(0))
                    anchor.copy(positionMs = position, playing = anchor.playing && elapsed <= 2000,
                        clockAnchorWallNs = now, clockAnchorSongUs = position * 1000)
                }
                mutableState.update { it.copy(playback = playback) }
                delay(20)
            }
        }
        scope.launch {
            try {
                val listener = ServerSocket(0)
                server = listener
                withContext(Dispatchers.Main) { discovery.advertise(listener.localPort) }
                mutableState.update { it.copy(port = listener.localPort, message = "Ready to pair") }
                while (isActive && !closed) {
                    val socket = listener.accept()
                    client = socket
                    try { socket.use { serve(CastWire(it, CastWire.secret(pairingKey), receiver = true)) } }
                    catch (error: Exception) {
                        if (!closed) mutableState.update { it.copy(message = "Connection interrupted. Reconnect from the phone.") }
                    } finally {
                        engine.pause(); client = null
                        remotePlayback = remotePlayback.copy(playing = false)
                        cache.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
                    }
                }
            } catch (error: Exception) {
                if (!closed) mutableState.update { it.copy(message = "Receiver unavailable: ${error.message}") }
            }
        }
    }

    private suspend fun serve(wire: CastWire) {
        var pending: File? = null
        var stream: FileOutputStream? = null
        var expectedHash = ""
        var expectedSize = 0L
        var receivedSize = 0L
        try {
            while (currentCoroutineContext().isActive) {
                val request = wire.receive()
                val receivedAt = System.nanoTime()
                val reply = JSONObject().put("type", "ok")
                when (request.getString("type")) {
                    "asset" -> {
                        check(stream == null)
                        expectedHash = request.getString("hash")
                        require(expectedHash.matches(Regex("[0-9a-f]{64}")))
                        expectedSize = request.getLong("size")
                        require(expectedSize in 1..CastController.MAX_ASSET)
                        val destination = File(cache, expectedHash)
                        val cached = destination.isFile && destination.length() == expectedSize && CastWire.digest(destination) == expectedHash
                        reply.put("cached", cached)
                        if (cached) destination.setLastModified(System.currentTimeMillis()) else {
                            require(cache.usableSpace > expectedSize + 64 * 1024 * 1024) { "Not enough TV storage for this media" }
                            // Bound reusable cache to 2 GB; never evict the assets used by the current Stage.
                            val protected = setOf(loadedMidi, loadedFont, mutableState.value.library.backgroundVideoUri?.let { Uri.parse(it).lastPathSegment })
                            var total = cache.listFiles()?.sumOf { it.length() } ?: 0
                            cache.listFiles()?.sortedBy { it.lastModified() }?.forEach { f ->
                                if (total + expectedSize > 2L * CastController.MAX_ASSET && f.name !in protected) {
                                    val size = f.length(); if (f.delete()) total -= size
                                }
                            }
                            require(total + expectedSize <= 2L * CastController.MAX_ASSET) { "TV cache is full. Use smaller media." }
                            pending = File(cache, "$expectedHash.part")
                            stream = FileOutputStream(pending)
                            receivedSize = 0
                        }
                    }
                    "chunk" -> {
                        val output = checkNotNull(stream)
                        val bytes = Base64.decode(request.getString("bytes"), Base64.NO_WRAP)
                        require(bytes.size <= 64 * 1024 && receivedSize + bytes.size <= expectedSize)
                        output.write(bytes); receivedSize += bytes.size
                    }
                    "commit" -> {
                        checkNotNull(stream).close(); stream = null
                        val file = checkNotNull(pending)
                        require(receivedSize == expectedSize && CastWire.digest(file) == expectedHash) { "Media checksum mismatch" }
                        val target = File(cache, expectedHash)
                        java.nio.file.Files.move(file.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        pending = null
                    }
                    "settings" -> {
                        val data = request.getJSONObject("data")
                        val assets = data.getJSONObject("assets")
                        fun asset(name: String): File? = assets.getString(name).takeIf { it.isNotEmpty() }?.let {
                            require(it.matches(Regex("[0-9a-f]{64}")))
                            File(cache, it).also { file -> require(file.isFile) { "Missing $name asset" } }
                        }
                        val midi = asset("midi"); val font = asset("font"); val video = asset("video")
                        if (midi != null && font != null && (midi.name != loadedMidi || font.name != loadedFont)) {
                            engine.pause()
                            engine.setSoundFont(font); engine.load(midi)
                            loadedMidi = midi.name; loadedFont = font.name
                        } else if (midi == null) { engine.stop(); loadedMidi = "" }
                        engine.setTranspose(data.getInt("key")); engine.setMelodyEnabled(data.getBoolean("melody"))
                        engine.setMixerConfig(StageCodec.mixer(data.getJSONObject("mixer")))
                        mutableState.update { it.copy(library = StageCodec.library(data).copy(
                            backgroundVideoUri = video?.let { f -> Uri.fromFile(f).toString() }), title = data.getString("title")) }
                    }
                    "lyrics" -> {
                        val lyrics = StageCodec.decodeLyrics(request.getJSONObject("data"))
                        engine.applyResolvedLyrics(lyrics)
                        remotePlayback = remotePlayback.copy(lyricLines = lyrics.lyricLines,
                            syncSourceType = lyrics.sourceType, syncConfidence = lyrics.confidence)
                    }
                    "tick" -> {
                        val playing = request.getBoolean("playing")
                        val tempo = request.getDouble("tempo").toFloat().coerceIn(0.5f, 1.5f)
                        val duration = request.getLong("duration").coerceAtLeast(0)
                        val position = StageClock.target(request.getLong("position"), duration, playing, tempo, request.getLong("transit"))
                        val outputOnTv = request.getBoolean("audio")
                        val drift = position - engine.state.value.positionMs
                        if (engine.state.value.tempoMultiplier != tempo) engine.setTempoMultiplier(tempo)
                        engine.setLatencyOffsetMs(request.getLong("offset").coerceIn(-4000, 4000))
                        if (outputOnTv && loadedMidi.isNotEmpty()) {
                            if (StageClock.needsSeek(engine.state.value.positionMs, position, playing, !tvAudio)) engine.seek(position)
                            if (playing && !engine.state.value.playing) engine.play()
                            if (!playing) engine.pause()
                        } else engine.pause()
                        tvAudio = outputOnTv
                        val levels = request.getJSONArray("levels")
                        remotePlayback = remotePlayback.copy(file = loadedMidi.takeIf { it.isNotEmpty() }?.let { File(cache, it) },
                            playing = playing, positionMs = position, durationMs = duration, tempoMultiplier = tempo,
                            effectiveLatencyOffsetMs = request.getLong("offset").coerceIn(-4000, 4000),
                            channelLevels = List(minOf(16, levels.length())) { levels.getDouble(it).toFloat().coerceIn(0f, 1f) })
                        lastTick = System.nanoTime()
                        mutableState.update { it.copy(session = true, message = "Connected") }
                        reply.put("drift", drift)
                    }
                    "disconnect" -> {
                        engine.pause(); tvAudio = false; remotePlayback = remotePlayback.copy(playing = false)
                        mutableState.update { it.copy(session = false, message = "Ready to pair") }
                        wire.send(reply); return
                    }
                    else -> error("Unsupported receiver command")
                }
                reply.put("processingMs", (System.nanoTime() - receivedAt) / 1_000_000)
                wire.send(reply)
            }
        } catch (error: Exception) {
            runCatching { wire.send(JSONObject().put("type", "error").put("error", error.message ?: "Receiver failed")) }
            throw error
        } finally { stream?.close(); pending?.delete() }
    }

    fun close() {
        closed = true
        discovery.close()
        runCatching { client?.close() }; runCatching { server?.close() }
        scope.cancel()
        // A native SoundFont load may be completing on IO. Release only after that worker exits.
        CoroutineScope(Dispatchers.IO).launch {
            scope.coroutineContext[Job]?.join()
            engine.release()
        }
    }
}
