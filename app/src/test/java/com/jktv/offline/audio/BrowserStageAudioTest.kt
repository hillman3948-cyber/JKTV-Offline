package com.jktv.offline.audio

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream

class BrowserStageAudioTest {
    private var time=1000L
    private var muted=false
    private var latency=0L
    private val router=BrowserStageAudio({time},{muted=it},{latency=it})
    private fun claim(client:String="screen_one")=router.claim(client,true).getString("token")
    @Test fun preparingAndNativeWaitLeaveHostAudible() {
        val token=claim();assertFalse(muted)
        assertEquals("waiting",router.commit("screen_one",token,false,200).getString("state"))
        assertFalse(muted)
        assertEquals("active",router.commit("screen_one",token,true,200).getString("state"))
        assertTrue(muted);assertEquals(200L,latency)
    }
    @Test fun lostClientRestoresHostAfterLeaseAndDrain() {
        val token=claim();router.commit("screen_one",token,true,180)
        time+=BrowserStageAudio.LEASE_MS;router.tick();assertTrue(muted)
        time+=BrowserStageAudio.DRAIN_MS;router.tick();assertFalse(muted);assertEquals(0L,latency)
    }
    @Test fun takeoverWaitsForPreviousAdvertisedLease() {
        val first=claim();router.commit("screen_one",first,true,180)
        val second=claim("screen_two")
        assertEquals("revoked",router.read("screen_one",first,0,180).state)
        assertEquals("waiting",router.commit("screen_two",second,true,180).getString("state"))
        time+=BrowserStageAudio.LEASE_MS+BrowserStageAudio.DRAIN_MS;router.tick()
        assertEquals("active",router.commit("screen_two",second,true,180).getString("state"))
        assertEquals("revoked",router.claim("screen_one",false).getString("state"))
    }
    @Test fun releaseCannotAffectAnotherOwnersToken() {
        val token=claim();router.commit("screen_one",token,true,180)
        router.release("screen_two",token);time+=300;router.tick();assertTrue(muted)
        router.release("screen_one",token);time+=250;router.tick();assertFalse(muted)
    }
    @Test fun authenticatedReadsRenewWithoutReplacingToken() {
        val token=claim();router.commit("screen_one",token,true,180)
        time+=1200;assertEquals("active",router.read("screen_one",token,0,230).state)
        assertEquals(token,router.claim("screen_one",false).getString("token"))
        time+=1000;router.tick();assertTrue(muted);assertEquals(230L,latency)
        assertEquals("expired",router.read("screen_two",token,0,180).state)
    }
    @Test fun nativeTakeoverDoesNotRestoreAudioBeforeRemoteDeadline() {
        val token=claim();router.commit("screen_one",token,true,180);router.revokeAll()
        time+=1000;router.tick();assertTrue(muted)
        time+=BrowserStageAudio.LEASE_MS+BrowserStageAudio.DRAIN_MS-1000;router.tick();assertFalse(muted)
    }
    @Test fun abandonedPreparationNeverMutesHost() {
        claim();time+=6000;router.tick();assertFalse(muted)
    }
    @Test fun pcmIsBoundedOrderedAndRevisionIsFlushed() {
        val buffer=StagePcmBuffer()
        repeat(220){buffer.offer(1,it.toDouble(),time.toDouble(),1f,true,byteArrayOf(1,2,3,4))}
        DataInputStream(ByteArrayInputStream(buffer.encode(1))).use { input ->
            assertEquals(0x4a4b4155,input.readInt());assertEquals(44100,input.readInt());assertEquals(2,input.readInt())
            assertEquals(128,input.readInt());assertEquals(2L,input.readLong());assertEquals(1L,input.readLong())
            assertEquals(1.0,input.readDouble(),0.0);assertEquals(time.toDouble(),input.readDouble(),0.0)
            assertEquals(1f,input.readFloat());assertEquals(1,input.readInt());assertEquals(4,input.readInt())
            assertArrayEquals(byteArrayOf(1,2,3,4),ByteArray(4).also {input.readFully(it)})
        }
        buffer.offer(2,9000.0,2000.0,1.5f,false,byteArrayOf(0,0,0,0))
        DataInputStream(ByteArrayInputStream(buffer.encode(0))).use {input ->
            repeat(3){input.readInt()};assertEquals(1,input.readInt());assertEquals(221L,input.readLong());assertEquals(2L,input.readLong())
        }
    }
}
