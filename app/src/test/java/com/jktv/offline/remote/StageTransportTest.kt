package com.jktv.offline.remote

import org.junit.Test
import org.junit.Assert.*
import java.io.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

class StageTransportTest {
    @Test fun guestQrDecodesExactOfflineRemoteUrl() {
        listOf("192.168.1.33:8383","192.168.123.222:65535").forEach {host ->
            val url="http://$host/?guest=0123456789abcdef01234567&view=remote"
            val matrix=QrCodeMatrix.encodeUrl(url);val edge=(matrix.size+8)*6
            val pixels=IntArray(edge*edge){i->val x=i%edge/6-4;val y=i/edge/6-4
                if(x in matrix.indices&&y in matrix.indices&&matrix[y][x])0xff000000.toInt() else 0xffffffff.toInt()}
            val bitmap=BinaryBitmap(HybridBinarizer(RGBLuminanceSource(edge,edge,pixels)))
            assertEquals(url,MultiFormatReader().decode(bitmap).text)
        }
    }
    @Test fun websocketHandshakeMatchesRfcExample() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",StageWebSocket.accept("dGhlIHNhbXBsZSBub25jZQ=="))
    }
    private fun masked(op:Int,text:String,fin:Boolean=true):ByteArray {
        val bytes=text.toByteArray();val key=byteArrayOf(1,2,3,4)
        return byteArrayOf(((if(fin)128 else 0) or op).toByte(),(128 or bytes.size).toByte())+key+bytes.mapIndexed {i,b->(b.toInt() xor key[i%4].toInt()).toByte()}.toByteArray()
    }
    @Test fun fragmentedTextHandlesInterleavedPing() {
        val out=ByteArrayOutputStream()
        val wire=StageWebSocket(ByteArrayInputStream(masked(1,"hel",false)+masked(9,"ping")+masked(0,"lo")),out)
        assertEquals("hello",wire.readText());assertEquals(0x8a,out.toByteArray()[0].toInt() and 255)
    }
    @Test fun binaryFramesRetainLargePcmPayload() {
        val bytes=ByteArray(130000){it.toByte()};val out=ByteArrayOutputStream()
        StageWebSocket(ByteArrayInputStream(byteArrayOf()),out).binary(bytes)
        val input=DataInputStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals(0x82,input.readUnsignedByte());assertEquals(127,input.readUnsignedByte());assertEquals(bytes.size.toLong(),input.readLong())
        assertArrayEquals(bytes,ByteArray(bytes.size).also{input.readFully(it)})
    }
    @Test(expected=IllegalArgumentException::class) fun unmaskedClientFrameRejected(){
        StageWebSocket(ByteArrayInputStream(byteArrayOf(0x81.toByte(),1,65)),ByteArrayOutputStream()).readText()
    }
    @Test fun headerReaderPreservesFollowingFrameBytes(){
        val input=ByteArrayInputStream("Host: local\r\n\r\n".toByteArray()+masked(1,"ok"))
        assertEquals("Host: local",StageWebSocket.readHttpLine(input));assertEquals("",StageWebSocket.readHttpLine(input))
        assertEquals("ok",StageWebSocket(input,ByteArrayOutputStream()).readText())
    }
}
