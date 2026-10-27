package com.jktv.offline.remote

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/** Bounded RFC 6455 framing for the local Stage request/response stream. No extensions negotiated. */
class StageWebSocket(private val input: InputStream, private val output: OutputStream) {
    fun readText(): String? {
        val message=java.io.ByteArrayOutputStream()
        var fragmented=false
        while(true) {
            val first=input.read();if(first<0)return null
            val second=readByte()
            require(first and 0x70==0 && second and 0x80!=0) { "Invalid WebSocket flags" }
            val fin=first and 0x80!=0;val opcode=first and 15
            var size=(second and 127).toLong()
            if(size==126L)size=(readByte()*256+readByte()).toLong()
            else if(size==127L){size=0;repeat(8){size=(size shl 8) or readByte().toLong()};require(size>=0)}
            require(size<=8192 && message.size()+size<=8192) { "WebSocket message too large" }
            if(opcode>=8)require(fin&&size<=125)
            val mask=readBytes(4);val bytes=readBytes(size.toInt())
            bytes.indices.forEach {bytes[it]=(bytes[it].toInt() xor mask[it%4].toInt()).toByte()}
            when(opcode) {
                8->{send(8,bytes);return null}
                9->send(10,bytes)
                10->Unit
                1,0->{
                    require((opcode==1&&!fragmented)||(opcode==0&&fragmented))
                    message.write(bytes);fragmented=!fin
                    if(fin)return message.toString("UTF-8")
                }
                else->error("Unsupported WebSocket opcode")
            }
        }
    }
    fun text(value:String)=send(1,value.toByteArray(Charsets.UTF_8))
    fun binary(value:ByteArray)=send(2,value)
    @Synchronized fun send(opcode:Int,bytes:ByteArray) {
        output.write(0x80 or opcode)
        when {
            bytes.size<126->output.write(bytes.size)
            bytes.size<=65535->{output.write(126);output.write(bytes.size ushr 8);output.write(bytes.size and 255)}
            else->{output.write(127);for(i in 7 downTo 0)output.write(((bytes.size.toLong() ushr (i*8)) and 255).toInt())}
        }
        output.write(bytes);output.flush()
    }
    private fun readByte()=input.read().also {require(it>=0){"Closed WebSocket"}}
    private fun readBytes(size:Int)=ByteArray(size).also { bytes ->
        var offset=0;while(offset<size){val n=input.read(bytes,offset,size-offset);require(n>0);offset+=n}
    }
    companion object {
        fun accept(key:String):String {
            require(Base64.getDecoder().decode(key).size==16)
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
        }
        /** Avoid BufferedReader prefetch consuming the first binary WebSocket bytes. */
        fun readHttpLine(input:InputStream):String? {
            val bytes=java.io.ByteArrayOutputStream()
            while(bytes.size()<8192){val b=input.read();if(b<0)return if(bytes.size()==0)null else bytes.toString("UTF-8")
                if(b==10)return bytes.toString("UTF-8").trimEnd('\r');bytes.write(b)}
            error("HTTP header too long")
        }
    }
}
