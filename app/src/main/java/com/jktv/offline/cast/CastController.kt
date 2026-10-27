package com.jktv.offline.cast

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.jktv.offline.LibraryUiState
import com.jktv.offline.playback.PlaybackState
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class CastPhase { Disconnected, Searching, Connecting, Connected, Reconnecting }
data class CastStatus(
    val phase: CastPhase = CastPhase.Disconnected,
    val device: String = "", val detail: String = "", val rttMs: Long = 0,
    val driftMs: Long = 0, val audioOnTv: Boolean = false
)
data class CastSnapshot(val library: LibraryUiState, val playback: PlaybackState, val soundFont: File?)

class CastController(
    private val context: Context,
    private val snapshot: () -> CastSnapshot,
    private val mutePhone: (Boolean) -> Unit,
    private val beforeTvAudio: suspend () -> Unit = {}
) {
    val discovery = CastDiscovery(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableStatus = MutableStateFlow(CastStatus())
    val status = mutableStatus.asStateFlow()
    private var connection: Job? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var tvAudio = false
    private var audioSwitchJob: Job? = null
    @Volatile private var stopping = false
    private var lastDevice: CastDevice? = null
    private var lastSecret: String = ""
    private val hashes = mutableMapOf<String, String>()
    private val imported = mutableMapOf<String, File>()
    private val preferences = context.getSharedPreferences("jktv-cast", Context.MODE_PRIVATE)
    var compensationMs: Long = preferences.getLong("compensation", 0L).coerceIn(-2000, 2000)
        private set

    fun setCompensation(value: Long) {
        compensationMs = value.coerceIn(-2000, 2000)
        preferences.edit().putLong("compensation", compensationMs).apply()
    }
    fun setTvAudio(enabled: Boolean) {
        audioSwitchJob?.cancel()
        if (!enabled) { tvAudio = false; return }
        audioSwitchJob = scope.launch { beforeTvAudio(); tvAudio = true }
    }
    fun search() {
        discovery.search()
        if (connection?.isActive != true) mutableStatus.value = CastStatus(CastPhase.Searching)
    }
    fun stopSearch() {
        discovery.stopSearch()
        if (mutableStatus.value.phase == CastPhase.Searching) mutableStatus.value = CastStatus()
    }
    fun retry() { lastDevice?.let { connect(it, lastSecret) } ?: search() }

    fun connect(device: CastDevice, pairingKey: String) {
        if (connection?.isActive == true) return
        val secret = runCatching { CastWire.secret(pairingKey) }.getOrElse {
            mutableStatus.value = CastStatus(detail = it.message.orEmpty()); return
        }
        require(device.port in 1..65535)
        lastDevice = device; lastSecret = pairingKey; stopping = false
        discovery.stopSearch()
        connection = scope.launch {
            var attempts = 0
            while (isActive && !stopping && attempts < 6) {
                mutableStatus.value = mutableStatus.value.copy(
                    phase = if (attempts == 0) CastPhase.Connecting else CastPhase.Reconnecting,
                    device = device.name, detail = if (attempts == 0) "Authorizing TV…" else "Retry ${attempts}/5…")
                try {
                    withContext(Dispatchers.IO) {
                        val client = Socket()
                        socket = client
                        client.connect(InetSocketAddress(device.host, device.port), 4000)
                        client.use {
                            val wire = CastWire(client, secret, receiver = false)
                            var previousMeta = ""
                            var previousLines: Any? = null
                            var previousSource = ""
                            var previousMedia = ""
                            val sentAssets = mutableSetOf<String>()
                            var rtt = 0L
                            while (isActive && !stopping) {
                                val captured = withContext(Dispatchers.Main) { snapshot() }
                                val p = captured.playback
                                val s = captured.library
                                val assets = linkedMapOf<String, String>()
                                val files = linkedMapOf("midi" to p.file, "font" to captured.soundFont,
                                    "video" to s.backgroundVideoUri?.let { materialize(it) })
                                client.soTimeout = 60000 // Large SF2 verification and native loading can exceed a heartbeat timeout.
                                for ((kind, file) in files) {
                                    if (file == null) { assets[kind] = ""; continue }
                                    val identity = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
                                    val hash = hashes.getOrPut(identity) { CastWire.digest(file) }
                                    assets[kind] = hash
                                    if (sentAssets.add(hash)) {
                                        withContext(Dispatchers.Main) { mutableStatus.value = mutableStatus.value.copy(detail = "Preparing $kind on TV…") }
                                        transfer(wire, file, hash)
                                    }
                                }
                                sentAssets.retainAll(assets.values.toSet())
                                val mediaIdentity = "${assets["midi"]}:${assets["font"]}"
                                if (mediaIdentity != previousMedia) { previousLines = null; previousMedia = mediaIdentity }
                                val meta = StageCodec.settings(s).put("assets", JSONObject(assets as Map<*, *>)).toString()
                                if (meta != previousMeta) {
                                    wire.send(JSONObject().put("type", "settings").put("data", JSONObject(meta)))
                                    checkReply(wire.receive()); previousMeta = meta
                                }
                                val source = "${p.syncSourceType}:${p.syncConfidence}"
                                if (previousLines !== p.lyricLines || previousSource != source) {
                                    wire.send(JSONObject().put("type", "lyrics").put("data", StageCodec.lyrics(p)))
                                    checkReply(wire.receive()); previousLines = p.lyricLines; previousSource = source
                                }
                                // Read again after asset preparation: never send a stale pre-transfer playback position.
                                client.soTimeout = 5000
                                val latest = withContext(Dispatchers.Main) { snapshot() }
                                val current = latest.playback
                                if (current.file != p.file) continue
                                val audio = tvAudio
                                val calibration = latest.library.latencyCalibration
                                val lyricOffset = if (audio) calibration.globalOffsetMs + calibration.perSongOffsetMs
                                    else current.effectiveLatencyOffsetMs
                                // Mute before granting the receiver an audio lease. Unmute only after its ack/expiry.
                                if (audio) withContext(Dispatchers.Main) { mutePhone(true) }
                                val start = System.nanoTime()
                                val livePosition = if (current.playing && current.clockAnchorWallNs > 0) {
                                    current.clockAnchorSongUs / 1000 + (((start - current.clockAnchorWallNs) / 1_000_000)
                                        .coerceIn(0, 80) * current.tempoMultiplier).toLong()
                                } else current.positionMs
                                wire.send(JSONObject().put("type", "tick").put("position", livePosition)
                                    .put("duration", current.durationMs).put("playing", current.playing)
                                    .put("tempo", current.tempoMultiplier).put("audio", audio)
                                    .put("transit", (rtt / 2).coerceIn(0, 250))
                                    .put("offset", lyricOffset + compensationMs)
                                    .put("levels", org.json.JSONArray(current.channelLevels)))
                                val ack = wire.receive(); checkReply(ack)
                                rtt = ((System.nanoTime() - start) / 1_000_000 - ack.optLong("processingMs")).coerceAtLeast(0)
                                if (!audio) withContext(Dispatchers.Main) { mutePhone(false) }
                                attempts = 0
                                withContext(Dispatchers.Main) {
                                    mutableStatus.value = CastStatus(CastPhase.Connected, device.name, "Stage ready", rtt,
                                        ack.optLong("drift"), audio)
                                }
                                delay(50)
                            }
                            if (stopping) {
                                wire.send(JSONObject().put("type", "disconnect"))
                                checkReply(wire.receive())
                            }
                        }
                    }
                    break
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    attempts++
                    mutableStatus.value = mutableStatus.value.copy(phase = CastPhase.Reconnecting,
                        detail = "${error.message ?: "TV connection interrupted"}. Check the TV pairing key and Wi-Fi.")
                    // The TV audio watchdog expires after 2 seconds; prevent echo on uncertain delivery.
                    delay(2300)
                    mutePhone(false)
                    mutableStatus.value = mutableStatus.value.copy(audioOnTv = false)
                    if (attempts < 6 && !stopping) delay((1000L shl (attempts - 1)).coerceAtMost(8000))
                } finally { runCatching { socket?.close() }; socket = null }
            }
            mutePhone(false)
            mutableStatus.value = mutableStatus.value.copy(phase = CastPhase.Disconnected, audioOnTv = false,
                detail = if (stopping) "Disconnected" else "Could not reconnect. Check the TV, then Retry.")
        }
    }

    private fun checkReply(reply: JSONObject) {
        check(reply.optString("type") == "ok") { reply.optString("error", "Receiver rejected the request") }
    }

    private suspend fun transfer(wire: CastWire, file: File, hash: String) {
        require(file.length() in 1..MAX_ASSET) { "Media must be smaller than 1 GB" }
        wire.send(JSONObject().put("type", "asset").put("hash", hash).put("size", file.length()))
        val reply = wire.receive(); checkReply(reply)
        if (reply.optBoolean("cached")) return
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (stopping) throw java.io.IOException("Disconnected")
                val count = input.read(buffer); if (count < 0) break
                wire.send(JSONObject().put("type", "chunk").put("bytes", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP)))
                checkReply(wire.receive())
            }
        }
        wire.send(JSONObject().put("type", "commit")); checkReply(wire.receive())
    }

    private fun materialize(uriText: String): File {
        val uri = Uri.parse(uriText)
        if (uri.scheme == "file" || uri.scheme == null) return File(uri.path ?: uriText)
        return imported.getOrPut(uriText) {
            val dir = File(context.cacheDir, "cast-export").apply { mkdirs() }
            val file = File.createTempFile("video-", ".media", dir)
            try {
                val source = context.contentResolver.openInputStream(uri) ?: error("Background video is unavailable")
                source.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= MAX_ASSET) { "Background video exceeds 1 GB" }; output.write(buffer, 0, count)
                    }
                } }
                file
            } catch (error: Exception) { file.delete(); throw error }
        }
    }

    fun disconnect() {
        stopping = true
        stopSearch()
        if (connection?.isActive != true) { mutePhone(false); mutableStatus.value = CastStatus() }
    }
    fun close() {
        stopping = true
        discovery.close()
        runCatching { socket?.close() }
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            scope.coroutineContext[Job]?.join()
            imported.values.forEach { it.delete() }
            imported.clear()
        }
        // Engine is released by the owner; no second audible source is started here.
    }
    companion object { const val MAX_ASSET = 1024L * 1024 * 1024 }
}
