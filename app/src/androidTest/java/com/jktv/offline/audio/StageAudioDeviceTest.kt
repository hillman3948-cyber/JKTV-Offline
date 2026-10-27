package com.jktv.offline.audio

import androidx.test.platform.app.InstrumentationRegistry
import com.jktv.offline.LibraryUiState
import com.jktv.offline.data.SongEntity
import com.jktv.offline.playback.FluidSynthMidiEngine
import com.jktv.offline.remote.*
import com.jktv.offline.stage.StageSnapshot
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Exercises real JNI -> AudioTrack -> authenticated HTTP -> browser PCM, using procedural test assets. */
class StageAudioDeviceTest {
    @Test fun nativePcmAndBrowserHandoff() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val dir=File(context.cacheDir,"stage-audio-qa").apply {mkdirs()}
        fun asset(name:String)=File(dir,name).also { file -> instrumentation.context.assets.open(name).use {input->file.outputStream().use {input.copyTo(it)}} }
        val engine=FluidSynthMidiEngine()
        val nonzero=AtomicLong();val captured=AtomicLong()
        val router=BrowserStageAudio(muteHost={engine.setOutputMuted(it)},presentationLatency={engine.setAudioPresentationLatencyMs(it)})
        engine.pcmSink={revision,pos,clock,tempo,playing,bytes ->
            router.pcm.offer(revision,pos,clock,tempo,playing,bytes);captured.incrementAndGet()
            if(bytes.any {it.toInt()!=0})nonzero.incrementAndGet()
        }
        var ui=LibraryUiState(currentSong=SongEntity(id=1,title="Procedural timing test",artist="JKTV QA",relativePath="qa-clock.mid"))
        var complete=false
        val handler=object:RemoteCommandHandler {
            override fun getStageStatus()=RemoteStageStatus("Procedural timing test","JKTV QA","QA",engine.state.value.playing,0,100,true,emptyList())
            override suspend fun getWebStageFrame(knownLinesRevision:String,knownSettingsRevision:String)=
                StageSnapshot.encode(ui,engine.state.value,"android-audio-qa",knownLinesRevision,knownSettingsRevision)
                    .put("audio",router.status()).put("qa",JSONObject().put("muted",engine.isOutputMuted()).put("blocks",captured.get()).put("nonzero",nonzero.get()))
            override fun openWebStageMedia():RemoteStageMedia?=null
            override suspend fun stageAudioControl(action:String,client:String,token:String,gesture:Boolean,latencyMs:Long)=when(action){
                "claim"->router.claim(client,gesture);"commit"->router.commit(client,token,true,latencyMs);"release"->router.release(client,token);else->JSONObject()
            }
            override fun stageAudioRead(client:String,token:String,after:Long,latencyMs:Long)=router.read(client,token,after,latencyMs)
            override suspend fun searchSongs(query:String)=emptyList<SongEntity>()
            override fun reserveSong(songId:Long,singer:String,secondSinger:String,mode:String,playNow:Boolean){}
            override fun performAction(action:String){when(action){
                "pause"->engine.pause();"play"->engine.play();"seek"->engine.seek(12000)
                "style"->ui=ui.copy(lyricFontFamily="SERIF",lyricFontSizeScale=1.3f,visualizerStyle="AURORA_WAVES")
                "done"->complete=true
            }}
        }
        val server=WifiRemoteServer(handler,context.assets)
        try {
            engine.setSoundFont(asset("qa-sine.sf2"));engine.load(asset("qa-clock.mid"));engine.play()
            withTimeout(5000){while(nonzero.get()<10)delay(20)}
            assertTrue("Native PCM must contain music",nonzero.get()>0)
            val before=engine.state.value.positionMs;val count=nonzero.get()
            engine.setOutputMuted(true);delay(350)
            assertTrue("Muting Android must preserve streaming PCM",nonzero.get()>count)
            assertTrue("Muting must not restart the sequencer",engine.state.value.positionMs>before)
            engine.setOutputMuted(false)
            server.start(8383)
            val state=server.state.value
            File(context.filesDir,"audio-qa-session.json").writeText(JSONObject().put("pin",state.pin).put("port",state.port).toString())
            withTimeout(150000){while(!complete){router.tick();delay(25)}}
            assertTrue(captured.get()>100)
        } finally {server.stop();engine.release();dir.deleteRecursively()}
    }
}
