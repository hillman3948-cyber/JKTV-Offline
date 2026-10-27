package com.jktv.offline.remote

import com.jktv.offline.data.QueueRow
import com.jktv.offline.data.SongEntity
import com.jktv.offline.playback.PlaybackState
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class RemoteServerState(
    val running: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = 8383,
    val pin: String = "1234",
    val pairingUrl: String = "",
    val guestUrl: String = "",
    val connectedClientsCount: Int = 0,
    val lastRemoteAction: String = ""
)

data class RemoteStageStatus(
    val currentTitle: String,
    val currentArtist: String,
    val currentSinger: String,
    val playing: Boolean,
    val keyTranspose: Int,
    val tempoPercent: Int,
    val melodyEnabled: Boolean,
    val queue: List<QueueRow>
)

data class RemoteStageMedia(val input: InputStream, val length: Long, val mimeType: String = "video/mp4", val fontWeight: Int? = null, val variableFont: Boolean = false)

interface RemoteCommandHandler {
    fun getStageStatus(): RemoteStageStatus
    suspend fun getWebStageFrame(knownLinesRevision: String, knownSettingsRevision: String): JSONObject
    fun openWebStageMedia(): RemoteStageMedia?
    suspend fun stageAudioControl(action: String, client: String, token: String, gesture: Boolean, latencyMs: Long): JSONObject
    fun stageAudioRead(client: String, token: String, after: Long, latencyMs: Long): com.jktv.offline.audio.StageAudioChunk
    suspend fun searchSongs(query: String): List<SongEntity>
    fun reserveSong(songId: Long, singer: String, secondSinger: String, mode: String, playNow: Boolean)
    fun performAction(action: String)
}

/**
 * Embedded Zero-Dependency Local Wi-Fi HTTP Server (`ServerSocket`) for Smartphone QR Remote Control.
 */
class WifiRemoteServer(private val handler: RemoteCommandHandler, private val assets: android.content.res.AssetManager) {
    private val webResources = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(RemoteServerState())
    val state: StateFlow<RemoteServerState> = mutableState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val seenClients = java.util.concurrent.ConcurrentHashMap<String,Long>()
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val stageSockets = java.util.concurrent.ConcurrentHashMap<String,Socket>()
    private val requestIds = linkedMapOf<String,Long>()
    @Volatile private var guestToken = ""
    private var networkJob:Job?=null
    private var qrKey=""
    private var qrSvg=""
    private fun freshGuestToken()=ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") {"%02x".format(it)}
    @Synchronized private fun duplicateRequest(id:String):Boolean {
        if(id.isBlank())return false
        require(id.length<=120)
        val now=System.nanoTime()/1_000_000
        requestIds.entries.removeAll {now-it.value>120000}
        if(requestIds.containsKey(id))return true
        if(requestIds.size>=1024)requestIds.remove(requestIds.keys.first())
        requestIds[id]=now;return false
    }
    @Synchronized private fun guestQr():String {
        val url=mutableState.value.guestUrl
        if(qrKey!=url){
            val matrix=QrCodeMatrix.encodeUrl(url);val size=matrix.size+8
            qrSvg=buildString { append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $size $size\" shape-rendering=\"crispEdges\"><path fill=\"white\" d=\"M0 0h${size}v${size}H0z\"/><path fill=\"#050813\" d=\"")
                matrix.forEachIndexed {y,row->row.forEachIndexed {x,on->if(on)append("M${x+4} ${y+4}h1v1h-1z")}}
                append("\"/></svg>") }
            qrKey=url
        };return qrSvg
    }

    fun start(preferredPort: Int = 8383) {
        if (acceptJob?.isActive == true) return
        val pin = (1000 + Random.nextInt(9000)).toString()
        val ip = detectLocalIpv4Address()
        guestToken=freshGuestToken()

        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredPort))
            }
        }.getOrElse {
            ServerSocket(0).apply { reuseAddress = true }
        }

        val boundPort = socket.localPort
        serverSocket = socket
        val url = "http://$ip:$boundPort/?pin=$pin"
        mutableState.value = RemoteServerState(
            running = true,
            ipAddress = ip,
            port = boundPort,
            pin = pin,
            pairingUrl = url,
            guestUrl = "http://$ip:$boundPort/?guest=$guestToken&view=remote",
            connectedClientsCount = seenClients.size
        )

        networkJob=scope.launch {
            while(isActive){
                kotlinx.coroutines.delay(3000);refreshNetworkAddress()
                val now=System.nanoTime()/1_000_000
                seenClients.entries.removeIf { now-it.value>30000 }
                val count=seenClients.size
                if(mutableState.value.connectedClientsCount!=count)mutableState.value=mutableState.value.copy(connectedClientsCount=count)
            }
        }
        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                if(sockets.size>=48){client.close();continue}
                sockets.add(client)
                launch {
                    try { handleClient(client) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { if (error !is java.io.IOException) android.util.Log.w("JKTVRemote", "Connection ended: ${error.javaClass.simpleName}", error); runCatching { client.close() } }
                    finally {sockets.remove(client)}
                }
            }
        }
    }

    fun stop() {
        networkJob?.cancel();networkJob=null
        sockets.forEach {runCatching {it.close()}};sockets.clear();stageSockets.clear()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        seenClients.clear()
        mutableState.value = mutableState.value.copy(running = false, pairingUrl = "", guestUrl = "")
    }

    fun refreshNetworkAddress() {
        val current = mutableState.value
        if (!current.running) return
        val ip = detectLocalIpv4Address()
        if(ip==current.ipAddress)return
        mutableState.value = current.copy(
            ipAddress = ip,
            pairingUrl = "http://$ip:${current.port}/?pin=${current.pin}",
            guestUrl = "http://$ip:${current.port}/?guest=$guestToken&view=remote"
        )
    }

    private suspend fun handleClient(socket: Socket) {
        val client = socket
        try {
            client.soTimeout = 6000
            val remoteIp = client.inetAddress?.hostAddress.orEmpty()
            val input=client.getInputStream()
            val requestLine = StageWebSocket.readHttpLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val fullPath = parts[1]
            val qIdx = fullPath.indexOf('?')
            val path = if (qIdx >= 0) fullPath.substring(0, qIdx) else fullPath
            val queryStr = if (qIdx >= 0) fullPath.substring(qIdx + 1) else ""
            val params = parseQueryParams(queryStr)

            val headers = HashMap<String, String>()
            while (true) {
                val line = StageWebSocket.readHttpLine(input)
                if (line.isNullOrEmpty()) break
                val split = line.indexOf(':')
                if (split > 0) headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
            }

            val expectedPin = mutableState.value.pin
            val guest=params["guest"]==guestToken && guestToken.isNotEmpty()
            val authorized=params["pin"]==expectedPin
            if((guest||authorized)&&remoteIp.isNotBlank()){
                seenClients[remoteIp]=System.nanoTime()/1_000_000
                if(mutableState.value.connectedClientsCount!=seenClients.size)mutableState.value=mutableState.value.copy(connectedClientsCount=seenClients.size)
            }
            when {
                path == "/" || path == "/index.html" -> {
                    if(!guest&&!authorized)writeResponse(client,403,"text/plain","Open the current QR link shown on the JKTV host or TV.")
                    else writeResponse(client, 200, "text/html; charset=utf-8", buildWebRemoteHtml(if(guest) "" else expectedPin, if(guest) "remote" else params["view"].orEmpty().takeIf { it == "stage" || it == "remote" }.orEmpty(), if(guest)guestToken else ""))
                }
                path == "/stage-stream" -> {
                    if(!authorized)writeResponse(client,403,"text/plain","Invalid pairing PIN")
                    else stageStream(client,headers,params)
                }
                path == "/stage-qr.svg" -> {
                    if(!authorized)writeResponse(client,403,"text/plain","Invalid pairing PIN")
                    else writeResponse(client,200,"image/svg+xml",guestQr())
                }
                path == "/stage-audio-pcm" -> {
                    if (params["pin"] != expectedPin) writeResponse(client, 403, "application/json", "{\"state\":\"unauthorized\"}")
                    else {
                        val audio = handler.stageAudioRead(params["client"].orEmpty(), params["token"].orEmpty(),
                            params["after"]?.toLongOrNull() ?: 0L, params["latency"]?.toLongOrNull() ?: 180L)
                        if (audio.state != "active" && audio.state != "preparing") {
                            writeResponse(client, 403, "application/json", JSONObject().put("state", audio.state).toString())
                        } else {
                            val header="HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                                "Content-Length: ${audio.data.size}\r\nX-JKTV-Audio-State: ${audio.state}\r\n" +
                                "X-JKTV-Lease-Until: ${audio.leaseUntil}\r\nX-JKTV-Server-Time: ${audio.serverTime}\r\n" +
                                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); write(audio.data); flush() }
                        }
                    }
                }
                path == "/stage-font" -> {
                    if (params["pin"] != expectedPin) writeResponse(client, 403, "text/plain", "Invalid pairing PIN")
                    else {
                        val stem = when (params["family"]) {
                            "SERIF" -> "NotoSerif"
                            "MONOSPACE" -> "DroidSansMono"
                            "CONDENSED" -> "RobotoCondensed"
                            "CASUAL", "CURSIVE" -> "ComingSoon"
                            else -> "Roboto"
                        }
                        val weight = params["weight"]?.toIntOrNull() ?: 400
                        val suffix = when { weight >= 900 -> "Black"; weight >= 700 -> "Bold"; weight >= 500 -> "Medium"; weight <= 300 -> "Light"; else -> "Regular" }
                        val file = listOf("$stem-$suffix.ttf", "$stem-Regular.ttf", "$stem.ttf")
                            .map { java.io.File("/system/fonts", it) }.firstOrNull { it.isFile && it.canRead() }
                        if (file == null) writeResponse(client, 404, "text/plain", "Host system font unavailable")
                        else {
                            val actualWeight = when { "Black" in file.name -> 900; "Bold" in file.name -> 700; "Medium" in file.name -> 500; "Light" in file.name -> 300; else -> 400 }
                            val variable = runCatching {
                                java.io.DataInputStream(file.inputStream()).use { font ->
                                    font.readInt(); val tables = font.readUnsignedShort(); font.skipBytes(6)
                                    var found = false
                                    repeat(tables.coerceAtMost(256)) { if (font.readInt() == 0x66766172) found = true; font.skipBytes(12) }
                                    found
                                }
                            }.getOrDefault(false)
                            writeStageMedia(client, RemoteStageMedia(file.inputStream(), file.length(), "font/ttf", actualWeight, variable), null)
                        }
                    }
                }
                path in setOf("/stage-client.js", "/stage-audio.js", "/stage-remote.js", "/stage-compat.js", "/stage-link.js", "/stage-worker.js", "/stage-pcm.js", "/stage.css") -> {
                    val file = "stage/" + path.removePrefix("/")
                    val content = webResources.getOrPut(file) { assets.open(file).bufferedReader().use { it.readText() } }
                    writeResponse(client, 200, if (path.endsWith(".js")) "application/javascript; charset=utf-8" else "text/css; charset=utf-8", content)
                }
                path == "/stage-media" -> {
                    if (params["pin"] != expectedPin) {
                        writeResponse(client, 403, "text/plain; charset=utf-8", "Invalid pairing PIN")
                    } else {
                        val media = handler.openWebStageMedia()
                        if (media == null) writeResponse(client, 404, "text/plain; charset=utf-8", "No Stage background video")
                        else writeStageMedia(client, media, headers["range"])
                    }
                }
                path.startsWith("/api/") -> {
                    val reqPin = params["pin"].orEmpty()
                    if (reqPin != expectedPin && !(guest && path in setOf("/api/status","/api/search","/api/reserve","/api/control"))) {
                        val err = JSONObject().put("ok", false).put("error", "Invalid pairing PIN").toString()
                        writeResponse(client, 403, "application/json; charset=utf-8", err)
                        return
                    }
                    when (path) {
                        "/api/status" -> {
                            val status = handler.getStageStatus()
                            val queueArr = JSONArray()
                            for (q in status.queue.take(25)) {
                                queueArr.put(
                                    JSONObject()
                                        .put("id", q.id)
                                        .put("number", q.displayNumber)
                                        .put("title", q.title)
                                        .put("artist", q.artist)
                                        .put("singer", q.singer)
                                        .put("secondSinger", q.secondSinger)
                                        .put("mode", q.mode)
                                )
                            }
                            val json = JSONObject()
                                .put("ok", true)
                                .put("currentTitle", status.currentTitle)
                                .put("currentArtist", status.currentArtist)
                                .put("currentSinger", status.currentSinger)
                                .put("playing", status.playing)
                                .put("keyTranspose", status.keyTranspose)
                                .put("tempoPercent", status.tempoPercent)
                                .put("melodyEnabled", status.melodyEnabled)
                                .put("queue", queueArr)
                                .toString()
                            writeResponse(client, 200, "application/json; charset=utf-8", json)
                        }
                        "/api/stage" -> {
                            val frame = handler.getWebStageFrame(params["revision"].orEmpty(), params["settingsRevision"].orEmpty())
                            frame.put("sentTime", System.nanoTime() / 1_000_000.0)
                            writeResponse(client, 200, "application/json; charset=utf-8", frame.toString(), headers["accept-encoding"].orEmpty().contains("gzip"))
                        }
                        "/api/stage-audio" -> {
                            val result = handler.stageAudioControl(params["action"].orEmpty(), params["client"].orEmpty(),
                                params["token"].orEmpty(), params["gesture"] == "1", params["latency"]?.toLongOrNull() ?: 180L)
                            writeResponse(client, 200, "application/json; charset=utf-8", result.toString())
                        }
                        "/api/search" -> {
                            val q = params["q"].orEmpty()
                            val results = handler.searchSongs(q)
                            val arr = JSONArray()
                            for (s in results.take(60)) {
                                arr.put(
                                    JSONObject()
                                        .put("id", s.id)
                                        .put("number", s.displayNumber)
                                        .put("title", s.title)
                                        .put("artist", s.artist)
                                )
                            }
                            val json = JSONObject().put("ok", true).put("songs", arr).toString()
                            writeResponse(client, 200, "application/json; charset=utf-8", json)
                        }
                        "/api/reserve" -> {
                            val songId = params["songId"]?.toLongOrNull() ?: -1L
                            val singer = params["singer"].orEmpty().ifBlank { "Guest Singer" }
                            val secondSinger = params["secondSinger"].orEmpty()
                            val mode = params["mode"].orEmpty().ifBlank { "SOLO" }
                            val playNow = params["playNow"] == "1"
                            if (songId > 0L && !duplicateRequest(params["requestId"].orEmpty())) {
                                handler.reserveSong(songId, singer, secondSinger, mode, playNow)
                                mutableState.value = mutableState.value.copy(
                                    lastRemoteAction = "$singer reserved song #$songId"
                                )
                            }
                            val json = JSONObject().put("ok", songId > 0L).toString()
                            writeResponse(client, 200, "application/json; charset=utf-8", json)
                        }
                        "/api/control" -> {
                            val cmd = params["cmd"].orEmpty()
                            if (cmd.isNotBlank() && !duplicateRequest(params["requestId"].orEmpty())) {
                                handler.performAction(cmd)
                                mutableState.value = mutableState.value.copy(
                                    lastRemoteAction = "Remote command: $cmd"
                                )
                            }
                            val json = JSONObject().put("ok", true).toString()
                            writeResponse(client, 200, "application/json; charset=utf-8", json)
                        }
                        else -> {
                            writeResponse(client, 404, "application/json; charset=utf-8", """{"ok":false}""")
                        }
                    }
                }
                else -> {
                    writeResponse(client, 404, "text/plain; charset=utf-8", "Not found")
                }
            }
        } finally {
            // Half-close first so clients finish receiving large responses before the socket is released.
            if (!client.isClosed) {
                runCatching {
                    client.shutdownOutput()
                    client.soTimeout = 500
                    val input = client.getInputStream()
                    var remaining = 8192
                    val drain = ByteArray(1024)
                    while (remaining > 0) {
                        val count = input.read(drain, 0, minOf(drain.size, remaining))
                        if (count < 0) break
                        remaining -= count
                    }
                }
                runCatching { client.close() }
            }
        }
    }

    private suspend fun stageStream(socket:Socket,headers:Map<String,String>,params:Map<String,String>) {
        require(headers["upgrade"].equals("websocket",true)&&headers["sec-websocket-version"]=="13")
        val id=params["client"].orEmpty();require(id.matches(Regex("[A-Za-z0-9_-]{8,100}")))
        val accept=StageWebSocket.accept(headers["sec-websocket-key"].orEmpty())
        stageSockets.put(id,socket)?.let {if(it!==socket)runCatching {it.close()}}
        socket.tcpNoDelay=true;socket.soTimeout=5000
        socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n").toByteArray())
        val wire=StageWebSocket(socket.getInputStream(),socket.getOutputStream())
        var lastFrame=0L;var lyrics="";var settings=""
        var frameJob:Job?=null
        val pendingFrame=java.util.concurrent.atomic.AtomicReference<JSONObject?>()
        try {
            while(scope.isActive && !socket.isClosed){
                val request=JSONObject(wire.readText()?:break)
                val now=System.nanoTime()/1_000_000
                val response=JSONObject().put("type","tick").put("echo",request.optDouble("sent"))
                val token=request.optString("token")
                var audio:com.jktv.offline.audio.StageAudioChunk?=null
                if(token.isNotBlank()) {
                    audio=handler.stageAudioRead(id,token,if(request.optDouble("queued")>1.2)Long.MAX_VALUE else request.optLong("after"),request.optLong("latency",400))
                    response.put("audio",JSONObject().put("state",audio.state).put("leaseUntil",audio.leaseUntil).put("serverTime",audio.serverTime))
                }
                pendingFrame.getAndSet(null)?.let {frame->
                    frame.put("sentTime",System.nanoTime()/1_000_000.0)
                    lyrics=frame.optString("revision");settings=frame.optString("settingsRevision");response.put("frame",frame)
                }
                if(frameJob?.isActive!=true && (now-lastFrame>=150||(request.optBoolean("full")&&!response.has("frame")))){
                    val knownLyrics=if(request.optBoolean("full")) "" else lyrics
                    val knownSettings=if(request.optBoolean("full")) "" else settings
                    lastFrame=now
                    frameJob=scope.launch {pendingFrame.set(handler.getWebStageFrame(knownLyrics,knownSettings))}
                }
                wire.text(response.toString())
                audio?.takeIf {it.data.size>16}?.let {wire.binary(it.data)}
                // Bound request rate even for a broken client. Normal clients request every 40 ms.
                kotlinx.coroutines.delay(15)
            }
        } finally {frameJob?.cancel();stageSockets.remove(id,socket)}
    }

    private fun writeResponse(socket: Socket, statusCode: Int, contentType: String, body: String, gzip: Boolean = false) {
        val plain = body.toByteArray(Charsets.UTF_8)
        val compressed = gzip && plain.size > 4096
        val bytes = if (compressed) java.io.ByteArrayOutputStream().also { buffer ->
            java.util.zip.GZIPOutputStream(buffer).use { it.write(plain) }
        }.toByteArray() else plain
        val statusText = when (statusCode) {
            200 -> "OK"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" + (if (compressed) "Content-Encoding: gzip\r\nVary: Accept-Encoding\r\n" else "") +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8) + bytes)
        out.flush()
    }

    private fun writeStageMedia(socket: Socket, media: RemoteStageMedia, rangeHeader: String?) {
        media.input.use { input ->
            var start = 0L
            var end = media.length - 1
            var partial = false
            if (!rangeHeader.isNullOrBlank()) {
                val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(rangeHeader.trim())
                if (match == null) {
                    writeResponse(socket, 416, "text/plain; charset=utf-8", "Invalid range"); return
                }
                val requestedStart = match.groupValues[1].toLongOrNull()
                val requestedEnd = match.groupValues[2].toLongOrNull()
                if (requestedStart == null && requestedEnd != null) {
                    start = (media.length - requestedEnd).coerceAtLeast(0)
                } else start = requestedStart ?: 0L
                end = (if (requestedStart == null) media.length - 1 else (requestedEnd ?: (media.length - 1))).coerceAtMost(media.length - 1)
                if (start > end || start >= media.length) {
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${media.length}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush(); return
                }
                partial = true
            }
            var toSkip = start
            while (toSkip > 0) {
                val skipped = input.skip(toSkip)
                if (skipped <= 0) { if (input.read() < 0) break else toSkip-- } else toSkip -= skipped
            }
            val out = socket.getOutputStream()
            val length = end - start + 1
            val header = "HTTP/1.1 ${if (partial) 206 else 200} ${if (partial) "Partial Content" else "OK"}\r\n" +
                "Content-Type: ${media.mimeType}\r\nContent-Length: $length\r\nAccept-Ranges: bytes\r\n" +
                (media.fontWeight?.let { "X-JKTV-Font-Weight: $it\r\nX-JKTV-Font-Variable: ${media.variableFont}\r\n" } ?: "") +
                (if (partial) "Content-Range: bytes $start-$end/${media.length}\r\n" else "") +
                "Cache-Control: private, max-age=600\r\nConnection: close\r\n\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                out.write(buffer, 0, count); remaining -= count
            }
            out.flush()
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }

    fun detectLocalIpv4Address(): String {
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val prioritized = interfaces.sortedBy { nif ->
                val n = nif.name.lowercase()
                when {
                    n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan") -> 0
                    n.startsWith("eth") -> 1
                    else -> 2
                }
            }
            for (nif in prioritized) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress.orEmpty()
                        if (!host.startsWith("169.254.")) return host
                    }
                }
            }
        }
        return "127.0.0.1"
    }

    private fun buildWebRemoteHtml(defaultPin: String, initialView: String, guestKey: String = ""): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>JKTV Pro Stage Remote</title>
<style>
  :root { --bg:#070A14; --card:#11172B; --accent:#00F0FF; --pink:#FF2A85; --text:#F3F7FF; --muted:#8E9BBF; }
  * { box-sizing:border-box; margin:0; padding:0; font-family:system-ui,-apple-system,sans-serif; }
  body { background:var(--bg); color:var(--text); padding:14px; max-width:560px; margin:0 auto; }
  .hdr { display:flex; justify-content:space-between; align-items:center; padding:12px 14px; background:linear-gradient(135deg,#121B38,#1B1035); border:1px solid rgba(0,240,255,0.28); border-radius:16px; margin-bottom:12px; }
  .brand { font-weight:900; font-size:18px; letter-spacing:1px; color:var(--accent); }
  .badge { font-size:11px; padding:4px 9px; border-radius:99px; background:rgba(0,240,255,0.16); color:var(--accent); font-weight:700; }
  .card { background:var(--card); border:1px solid rgba(255,255,255,0.08); border-radius:14px; padding:12px; margin-bottom:12px; }
  .row { display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
  input, select { background:#0B1020; color:var(--text); border:1px solid rgba(255,255,255,0.16); border-radius:10px; padding:10px 12px; font-size:14px; width:100%; }
  button { background:#1A2442; color:var(--text); border:1px solid rgba(0,240,255,0.25); border-radius:10px; padding:9px 12px; font-weight:700; font-size:13px; cursor:pointer; }
  button.pri { background:linear-gradient(135deg,#00F0FF,#0099FF); color:#040814; border:none; }
  button.pink { background:linear-gradient(135deg,#FF2A85,#9B2DFF); color:#fff; border:none; }
  .ctrl-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:6px; margin-top:8px; }
  .song { display:flex; justify-content:space-between; align-items:center; padding:10px 0; border-bottom:1px solid rgba(255,255,255,0.07); gap:8px; }
  .song:last-child { border-bottom:none; }
  .num { font-family:monospace; color:var(--accent); font-weight:800; font-size:13px; }
  .title { font-weight:700; font-size:14px; }
  .artist { font-size:12px; color:var(--muted); }
  .sec-title { font-size:12px; font-weight:800; letter-spacing:1px; color:var(--accent); margin-bottom:8px; text-transform:uppercase; }
</style>
<link rel="stylesheet" href="/stage.css"/>
</head>
<body>
  <section id="chooser" class="chooser">
    <div class="brand">JKTV • TV DISPLAY</div>
    <h1>Choose what this screen shows</h1>
    <p>Use Stage on the TV and control songs from your phone.</p>
    <button id="stageChoice" class="choice pri">Show Karaoke Stage</button>
    <button id="remoteChoice" class="choice">Use Browser Remote</button>
  </section>
  <main id="tvStage" class="stage" hidden>
    <video id="stageVideo" muted playsinline loop hidden></video>
    <img id="stageImage" alt="" hidden/>
    <canvas id="stageCanvas" aria-hidden="true"></canvas>
    <div class="stage-scrim"></div>
    <header class="stage-info"><div id="stageTitle"></div><div id="stageSinger"></div></header>
    <div id="countdown" aria-live="off"></div>
    <div id="lyrics" class="stage-center" aria-label="Karaoke lyrics"></div>
    <aside id="guestQr" class="guest-qr" hidden><img id="guestQrImage" alt="Scan to open the JKTV browser remote"/><span>SCAN TO CONNECT</span></aside>
    <div id="stageConnection" role="status" hidden></div>
  </main>
  <main id="remoteView" hidden>
  <div class="hdr">
    <div>
      <div class="brand">JKTV NEON REMOTE</div>
      <div id="nowPlaying" style="font-size:12px;color:var(--muted);margin-top:2px;">Connecting to stage...</div>
    </div>
    <div class="badge" id="pinBadge">PIN $defaultPin</div>
  </div>

  <div class="card">
    <div class="sec-title">Singer Identity & Mode</div>
    <div class="row">
      <input id="singerInput" placeholder="Your Singer Name (e.g. Alex)" style="flex:1.2" />
      <select id="modeSelect" style="flex:0.8">
        <option value="SOLO">Solo Mode</option>
        <option value="DUET">Duet Mode</option>
        <option value="PARTY">Party Mode</option>
      </select>
    </div>
    <input id="partnerInput" placeholder="Duet Partner Name (Optional)" style="margin-top:8px;" />
  </div>

  <div class="card">
    <div class="sec-title">Live Stage Controls</div>
    <div class="ctrl-grid">
      <button onclick="sendCmd('PLAY_PAUSE')" class="pri">Play/Pause</button>
      <button onclick="sendCmd('NEXT')" class="pink">Skip Next</button>
      <button onclick="sendCmd('RESTART')">Restart</button>
      <button onclick="sendCmd('MELODY')">Melody</button>
      <button onclick="sendCmd('KEY_DOWN')">Key -</button>
      <button onclick="sendCmd('KEY_UP')">Key +</button>
      <button onclick="sendCmd('TEMPO_DOWN')">Tempo -</button>
      <button onclick="sendCmd('TEMPO_UP')">Tempo +</button>
    </div>
  </div>

  <div class="card">
    <div class="sec-title">Search 50,000+ Songs (Title, Artist or #Number)</div>
    <div class="row">
      <input id="searchBox" placeholder="Type title, artist, or 5-digit number..." oninput="debouncedSearch()" />
    </div>
    <div id="resultsList" style="margin-top:8px;max-height:320px;overflow-y:auto;"></div>
  </div>

  <div class="card">
    <div class="sec-title">Up Next Queue</div>
    <div id="queueList" style="font-size:13px;color:var(--muted);">Queue is empty</div>
  </div>

<script>
window.JKTV_PAIRING_PIN=(function(){var m=/[?&]pin=([^&]*)/.exec(location.search);try{return m?decodeURIComponent(m[1]):'$defaultPin';}catch(e){return '';}})();
window.JKTV_INITIAL_VIEW='$initialView';
window.JKTV_GUEST_KEY='$guestKey';
</script>
<script src="/stage-compat.js"></script>
<script src="/stage-remote.js"></script>
  </main>
<script src="/stage-pcm.js"></script>
<script src="/stage-link.js"></script>
<script src="/stage-audio.js"></script>
<script src="/stage-client.js"></script>
</body>
</html>
""".trimIndent()

    fun release() {
        stop()
        scope.cancel()
    }
}
