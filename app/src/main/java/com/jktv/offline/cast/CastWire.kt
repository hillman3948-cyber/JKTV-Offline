package com.jktv.offline.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Local-only, versioned, authenticated transport. Pairing secrets never travel over the socket. */
class CastWire(private val socket: Socket, secret: ByteArray, receiver: Boolean) {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())
    private val random = SecureRandom()
    private val key: SecretKeySpec
    private val sendDirection = if (receiver) 1 else 0
    private var sent = 0L
    private var received = 0L

    init {
        require(secret.size == 16)
        socket.soTimeout = 5000
        socket.tcpNoDelay = true
        val local = ByteArray(32).also(random::nextBytes)
        output.writeInt(0x4a4b5401)
        output.write(local)
        output.flush()
        require(input.readInt() == 0x4a4b5401) { "Incompatible JKTV receiver protocol" }
        val remote = ByteArray(32).also(input::readFully)
        val salt = if (receiver) local + remote else remote + local
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        key = SecretKeySpec(mac.doFinal(salt), "AES")
        send(JSONObject().put("type", "hello").put("version", 1))
        require(receive().getString("type") == "hello") { "Pairing failed" }
    }

    fun send(message: JSONObject) {
        val plain = message.toString().toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_FRAME - 32) { "Stage message too large" }
        val nonce = java.nio.ByteBuffer.allocate(12).putInt(sendDirection).putLong(sent++).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plain)
        output.writeInt(encrypted.size)
        output.write(encrypted)
        output.flush()
    }

    fun receive(): JSONObject {
        val size = input.readInt()
        require(size in 16..MAX_FRAME) { "Invalid Stage message length" }
        val bytes = ByteArray(size).also(input::readFully)
        val nonce = java.nio.ByteBuffer.allocate(12).putInt(1 - sendDirection).putLong(received++).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return JSONObject(String(cipher.doFinal(bytes), Charsets.UTF_8))
    }

    companion object {
        const val MAX_FRAME = 2 * 1024 * 1024
        fun newSecret(): String = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        fun secret(text: String): ByteArray {
            val normalized = text.filterNot { it.isWhitespace() || it == '-' }.lowercase()
            require(normalized.matches(Regex("[0-9a-f]{32}"))) { "Enter the 32-character pairing key shown on the TV" }
            return normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        fun digest(file: java.io.File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = stream.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
