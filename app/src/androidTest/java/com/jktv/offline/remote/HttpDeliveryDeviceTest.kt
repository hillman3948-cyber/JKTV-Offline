package com.jktv.offline.remote
import androidx.test.runner.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jktv.offline.audio.StageAudioChunk
import com.jktv.offline.data.SongEntity
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class HttpDeliveryDeviceTest {
    @Test fun largeIdentityResponsesArriveIntactOverAndroidTcp() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val body=JSONObject().put("payload","0123456789".repeat(40000))
        val handler=object:RemoteCommandHandler {
            override fun getStageStatus()=RemoteStageStatus("","","",false,0,100,true,emptyList())
            override suspend fun getWebStageFrame(knownLinesRevision:String,knownSettingsRevision:String)=JSONObject(body.toString())
            override fun openWebStageMedia():RemoteStageMedia?=null
            override suspend fun stageAudioControl(action:String,client:String,token:String,gesture:Boolean,latencyMs:Long)=JSONObject()
            override fun stageAudioRead(client:String,token:String,after:Long,latencyMs:Long)=StageAudioChunk(byteArrayOf(),"expired",0,0)
            override suspend fun searchSongs(query:String)=emptyList<SongEntity>()
            override fun reserveSong(songId:Long,singer:String,secondSinger:String,mode:String,playNow:Boolean){}
            override fun performAction(action:String){}
        }
        val server=WifiRemoteServer(handler,context.assets)
        try {
            server.start(0);val state=server.state.value
            repeat(30) { attempt ->
                Socket("127.0.0.1",state.port).use { socket ->
                    socket.soTimeout=5000
                    socket.getOutputStream().write(("GET /api/stage?pin=${state.pin} HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").toByteArray())
                    val raw=socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                    val header=raw.substringBefore("\r\n\r\n");val content=raw.substringAfter("\r\n\r\n")
                    val length=Regex("Content-Length: (\\d+)").find(header)!!.groupValues[1].toInt()
                    assertEquals("Response $attempt",length,content.toByteArray().size)
                    assertEquals(body.getString("payload"),JSONObject(content).getString("payload"))
                }
            }
        } finally { server.release() }
    }
}
