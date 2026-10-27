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
class StagePerformanceDeviceTest {
    @Test fun nativePcmAndBrowserHandoff() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val dir=File(context.cacheDir,"stage-audio-qa").apply {mkdirs()}
        fun asset(name:String)=File(dir,name).also { file -> instrumentation.context.assets.open(name).use {input->file.outputStream().use {input.copyTo(it)}} }
        val activity=instrumentation.startActivitySync(android.content.Intent(context,com.jktv.offline.MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        com.jktv.offline.playback.PlaybackKeepAliveService.start(context)
        val database=androidx.room.Room.inMemoryDatabaseBuilder(context,com.jktv.offline.data.KaraokeDatabase::class.java).build()
        val dao=database.karaokeDao()
        database.openHelper.writableDatabase.apply {
            beginTransaction()
            try{repeat(50000){i->execSQL("INSERT INTO songs (id,songNumber,title,artist,relativePath,durationMs,hasLyrics,favorite,playCount,lastPlayedAt,packageVersion,available) VALUES (?,?,?,?,?,0,0,0,0,NULL,'',1)",arrayOf<Any>(i+1,(i+1).toString().padStart(5,'0'),"Song ${i+1}","QA Artist ${i%100}","qa/${i+1}.mid"))};setTransactionSuccessful()}finally{endTransaction()}
        }
        val engine=FluidSynthMidiEngine()
        val nonzero=AtomicLong();val captured=AtomicLong()
        val router=BrowserStageAudio(muteHost={engine.setOutputMuted(it)},presentationLatency={engine.setAudioPresentationLatencyMs(it)})
        engine.pcmSink={revision,pos,clock,tempo,playing,bytes ->
            router.pcm.offer(revision,pos,clock,tempo,playing,bytes);captured.incrementAndGet()
            if(bytes.any {it.toInt()!=0})nonzero.incrementAndGet()
        }
        var ui=LibraryUiState(currentSong=SongEntity(id=1,title="Procedural timing test",artist="JKTV QA",relativePath="qa-long.mid"))
        val searches=AtomicLong();val reservations=AtomicLong();val commands=AtomicLong()
        val rows=java.util.concurrent.atomic.AtomicReference<List<com.jktv.offline.data.QueueRow>>(emptyList())
        var frameDelay=0L
        var complete=false
        lateinit var server:WifiRemoteServer
        val handler=object:RemoteCommandHandler {
            override fun getStageStatus()=RemoteStageStatus("Procedural timing test","JKTV QA","QA",engine.state.value.playing,0,100,true,rows.get())
            override suspend fun getWebStageFrame(knownLinesRevision:String,knownSettingsRevision:String):JSONObject {
                if(frameDelay>0)delay(frameDelay)
                return StageSnapshot.encode(ui.copy(remoteServerState=server.state.value),engine.state.value,"android-audio-qa",knownLinesRevision,knownSettingsRevision)
                    .put("audio",router.status()).put("qa",JSONObject().put("muted",engine.isOutputMuted()).put("blocks",captured.get()).put("nonzero",nonzero.get()).put("searches",searches.get()).put("reservations",reservations.get()).put("commands",commands.get()))
            }
            override fun openWebStageMedia():RemoteStageMedia?=null
            override suspend fun stageAudioControl(action:String,client:String,token:String,gesture:Boolean,latencyMs:Long)=when(action){
                "claim"->router.claim(client,gesture);"commit"->router.commit(client,token,true,latencyMs);"release"->router.release(client,token);else->JSONObject()
            }
            override fun stageAudioRead(client:String,token:String,after:Long,latencyMs:Long)=router.read(client,token,after,latencyMs)
            override suspend fun searchSongs(query:String):List<SongEntity> {searches.incrementAndGet();return com.jktv.offline.data.SongSearchEngine.search(dao,query,limit=60).songs}
            override fun reserveSong(songId:Long,singer:String,secondSinger:String,mode:String,playNow:Boolean){
                runBlocking {dao.addQueueItem(com.jktv.offline.data.QueueEntity(songId=songId,singer=singer,position=reservations.incrementAndGet().toInt(),secondSinger=secondSinger,mode=mode));rows.set(dao.queueRows())}
            }
            override fun performAction(action:String){commands.incrementAndGet();when(action){
                "pause"->engine.pause();"play"->engine.play();"seek"->engine.seek(12000)
                "style"->ui=ui.copy(lyricFontFamily="SERIF",lyricFontSizeScale=1.3f,visualizerStyle="AURORA_WAVES")
                "qr_off"->ui=ui.copy(stageGuestQrEnabled=false)
                "qr_on"->ui=ui.copy(stageGuestQrEnabled=true)
                "slow_frames"->frameDelay=450
                "fast_frames"->frameDelay=0
                "done"->complete=true
            }}
        }
        server=WifiRemoteServer(handler,context.assets)
        try {
            engine.setSoundFont(asset("qa-sine.sf2"));engine.load(asset("qa-long.mid"));engine.play()
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
            withTimeout(1200000){var renew=0;while(!complete){router.tick();if(++renew%2400==0)com.jktv.offline.playback.PlaybackKeepAliveService.start(context);delay(25)}}
            assertTrue(captured.get()>100)
        } finally {server.stop();engine.release();database.close();com.jktv.offline.playback.PlaybackKeepAliveService.stop(context);instrumentation.runOnMainSync{activity.finish()};dir.deleteRecursively()}
    }
}
