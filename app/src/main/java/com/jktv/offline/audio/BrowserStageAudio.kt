package com.jktv.offline.audio

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import org.json.JSONObject

data class StagePcmBlock(val sequence: Long, val revision: Long, val positionMs: Double,
    val capturedAtMs: Double, val tempo: Float, val playing: Boolean, val pcm: ByteArray)
data class StageAudioChunk(val data: ByteArray, val state: String, val leaseUntil: Long, val serverTime: Long)

class StagePcmBuffer {
    private val blocks = ArrayDeque<StagePcmBlock>()
    private var sequence = 0L
    @Synchronized fun offer(revision: Long, position: Double, captured: Double, tempo: Float, playing: Boolean, pcm: ByteArray) {
        if (blocks.lastOrNull()?.revision != revision) blocks.clear()
        blocks.addLast(StagePcmBlock(++sequence,revision,position,captured,tempo,playing,pcm))
        while (blocks.size > 1024) blocks.removeFirst()
    }
    fun encode(after: Long): ByteArray {
        val chosen = synchronized(this) { if (after <= 0) blocks.takeLast(48) else blocks.asSequence().filter { it.sequence > after }.take(128).toList() }
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(0x4a4b4155); output.writeInt(44100); output.writeInt(2); output.writeInt(chosen.size)
            chosen.forEach { block ->
                output.writeLong(block.sequence); output.writeLong(block.revision); output.writeDouble(block.positionMs)
                output.writeDouble(block.capturedAtMs); output.writeFloat(block.tempo)
                output.writeInt(if (block.playing) 1 else 0); output.writeInt(block.pcm.size); output.write(block.pcm)
            }
        }
        return bytes.toByteArray()
    }
}

/** Expiring, exclusive Stage audio ownership. The host sequencer is never stopped or sought here. */
class BrowserStageAudio(
    private val now: () -> Long = { System.nanoTime()/1_000_000 },
    private val muteHost: (Boolean) -> Unit,
    private val presentationLatency: (Long) -> Unit
) {
    private data class Lease(val client: String,val token: String,var until: Long,var renew: Boolean=true)
    private var owner: Lease? = null
    private var candidate: Lease? = null
    private var muted = false
    private var latency = 0L
    private val revoked = linkedSetOf<String>()
    val pcm = StagePcmBuffer()

    private fun token() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun reap() {
        val time=now()
        if (candidate?.until?.let { time>=it } == true) candidate=null
        if (owner?.until?.let { time>=it+DRAIN_MS } == true) owner=null
        val shouldMute=owner!=null
        if (muted!=shouldMute) { muted=shouldMute; muteHost(muted) }
        if (!muted&&latency!=0L) {latency=0;presentationLatency(0)}
    }
    @Synchronized fun tick() { reap() }
    @Synchronized fun claim(client:String,gesture:Boolean):JSONObject {
        require(client.matches(Regex("[A-Za-z0-9_-]{8,100}"))) { "Invalid Stage client" }
        reap()
        if (gesture) revoked.remove(client)
        if (client in revoked) return reply("revoked")
        owner?.takeIf { it.client==client && it.renew && now()<it.until }?.let { return reply("active",it) }
        candidate?.takeIf { it.client==client }?.let { return reply("preparing",it) }
        owner?.takeIf { it.client!=client }?.let { it.renew=false;revoked.add(it.client) }
        candidate?.takeIf { it.client!=client }?.let { revoked.add(it.client) }
        while (revoked.size>40) revoked.remove(revoked.first())
        val offer=Lease(client,token(),now()+5000)
        candidate=offer
        return reply("preparing",offer)
    }
    @Synchronized fun commit(client:String,token:String,nativeReady:Boolean,latencyMs:Long):JSONObject {
        reap()
        owner?.takeIf { valid(it,client,token)&&it.renew&&now()<it.until }?.let { return reply("active",it) }
        val offer=candidate?.takeIf { valid(it,client,token) } ?: return reply(if(client in revoked) "revoked" else "expired")
        if (!nativeReady || owner!=null) return reply("waiting",offer)
        val lease=Lease(client,token,now()+LEASE_MS)
        owner=lease;candidate=null
        muted=true;muteHost(true)
        latency=latencyMs.coerceIn(60,2000);presentationLatency(latency)
        return reply("active",lease)
    }
    @Synchronized fun read(client:String,token:String,after:Long,latencyMs:Long):StageAudioChunk {
        reap()
        val time=now()
        val active=owner?.takeIf { valid(it,client,token)&&it.renew&&time<it.until }
        val pending=candidate?.takeIf { valid(it,client,token) }
        val state=when {client in revoked->"revoked"; active!=null->"active";pending!=null->"preparing";else->"expired"}
        if (active!=null) {
            active.until=time+LEASE_MS
            val next=latencyMs.coerceIn(60,2000)
            if (kotlin.math.abs(next-latency)>8) {latency=next;presentationLatency(next)}
        }
        return StageAudioChunk(if(active!=null||pending!=null) pcm.encode(after) else ByteArray(0),state,active?.until?:0,time)
    }
    @Synchronized fun release(client:String,token:String):JSONObject {
        owner?.takeIf { valid(it,client,token) }?.let { it.renew=false;it.until=minOf(it.until,now()) }
        candidate?.takeIf { valid(it,client,token) }?.let { candidate=null }
        reap()
        return reply("released")
    }
    @Synchronized fun revokeAll() {
        // No remote acknowledgement yet: preserve the advertised deadline before restoring another output.
        owner?.let { revoked.add(it.client);it.renew=false }
        candidate?.let { revoked.add(it.client) };candidate=null;reap()
    }
    @Synchronized fun status():JSONObject {
        reap()
        return JSONObject().put("owner",owner?.client.orEmpty()).put("active",muted)
            .put("latencyMs",latency).put("leaseUntil",owner?.until?:0).put("serverTime",now())
    }
    private fun valid(lease:Lease,client:String,token:String)=lease.client==client&&lease.token==token&&token.isNotEmpty()
    private fun reply(state:String,lease:Lease?=null)=JSONObject().put("state",state)
        .put("token",lease?.token.orEmpty()).put("leaseUntil",if(state=="active")lease?.until?:0 else 0)
        .put("serverTime",now()).put("retryAfterMs",owner?.let { (it.until+DRAIN_MS-now()).coerceAtLeast(50) }?:100)
    companion object { const val LEASE_MS=3000L;const val DRAIN_MS=250L;const val CLIENT_SAFETY_MS=350L }
}

