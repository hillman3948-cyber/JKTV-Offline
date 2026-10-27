package com.jktv.offline.cast

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CastWireTest {
    @Test fun encryptedSessionDeliversOrderedCommandsAndAcknowledgements() {
        val secret = CastWire.secret(CastWire.newSecret())
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Boolean> {
                    server.accept().use { socket ->
                        val wire = CastWire(socket, secret, true)
                        repeat(20) { index ->
                            val command = wire.receive()
                            assertEquals(index, command.getInt("sequence"))
                            assertEquals("歌詞", command.getString("lyric"))
                            wire.send(JSONObject().put("type", "ok").put("sequence", index))
                        }
                    }
                    true
                }
                Socket("127.0.0.1", server.localPort).use { socket ->
                    val wire = CastWire(socket, secret, false)
                    assertTrue(runCatching { wire.send(JSONObject().put("oversized", "x".repeat(CastWire.MAX_FRAME))) }.isFailure)
                    repeat(20) { index ->
                        wire.send(JSONObject().put("sequence", index).put("lyric", "歌詞"))
                        assertEquals(index, wire.receive().getInt("sequence"))
                    }
                }
                assertTrue(result.get(10, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun wrongPairingKeyCannotAuthorize() {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Boolean> {
                    server.accept().use { socket ->
                        runCatching { CastWire(socket, CastWire.secret("00".repeat(16)), true) }.isFailure
                    }
                }
                Socket("127.0.0.1", server.localPort).use { socket ->
                    assertTrue(runCatching { CastWire(socket, CastWire.secret("11".repeat(16)), false) }.isFailure)
                }
                assertTrue(result.get(10, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun pairingKeyAcceptsDisplayGroupingAndRejectsShortPins() {
        assertArrayEquals(CastWire.secret("0123456789abcdef0123456789abcdef"),
            CastWire.secret("0123 4567 89AB CDEF 0123-4567-89AB-CDEF"))
        assertTrue(runCatching { CastWire.secret("1234") }.isFailure)
        assertTrue(runCatching { CastWire.secret("z".repeat(32)) }.isFailure)
    }

    @Test fun contentHashChangesWhenMediaChanges() {
        val file = java.io.File.createTempFile("jktv-test", ".mid")
        try {
            file.writeText("first"); val first = CastWire.digest(file)
            file.writeText("second"); assertNotEquals(first, CastWire.digest(file))
            assertEquals(64, first.length)
        } finally { file.delete() }
    }
}
