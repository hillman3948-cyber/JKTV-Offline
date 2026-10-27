@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.jktv.offline.audio

import dev.kotlinds.fluidsynthkmp.AudioConfig
import dev.kotlinds.fluidsynthkmp.FluidSynthJni

/**
 * Driverless access to the pinned fluidsynth-kmp 1.1.0 JNI ABI. Its public player always
 * creates an audio driver; rendering a second time would advance that synth twice.
 * Keep the dependency pinned and run StageAudioDeviceTest when changing its version.
 * One rendered PCM stream feeds AudioTrack and the authenticated browser receiver.
 */
class PcmSynth(config: AudioConfig) : AutoCloseable {
    private val jni = FluidSynthJni
    private var settings = 0L
    private var synth = 0L
    private val midi by lazy { FluidMidiControls() }
    init {
        settings = jni.newSettings()
        check(settings != 0L) { "FluidSynth settings allocation failed" }
        jni.setSettingsNum(settings,"synth.sample-rate",config.sampleRate.toDouble())
        jni.setSettingsInt(settings,"audio.period-size",config.periodSize)
        jni.setSettingsInt(settings,"audio.periods",config.periods)
        synth = jni.newSynth(settings)
        if (synth == 0L) { close();error("FluidSynth allocation failed") }
        jni.setInterpMethod(synth,-1,config.interpolation)
    }
    fun loadSoundFont(file:String):Int = jni.sfLoad(synth,file,true)
    fun noteOn(ch:Int,key:Int,velocity:Int) { jni.noteOn(synth,ch,key,velocity) }
    fun noteOff(ch:Int,key:Int) { jni.noteOff(synth,ch,key) }
    fun programChange(ch:Int,program:Int) { jni.programChange(synth,ch,program) }
    fun controlChange(ch:Int,number:Int,value:Int) { midi.control(synth,ch,number,value) }
    fun pitchBend(ch:Int,value:Int) { midi.pitchBend(synth,ch,value) }
    fun channelPressure(ch:Int,value:Int) { midi.channelPressure(synth,ch,value) }
    fun keyPressure(ch:Int,key:Int,value:Int) { midi.keyPressure(synth,ch,key,value) }
    fun resetMidi() { midi.reset(synth) }
    fun allSoundsOff(ch:Int) { midi.silence(synth,ch) }
    fun setGain(gain:Float) { jni.setGain(synth,gain) }
    fun setInterpolation(interpolation:Int) { jni.setInterpMethod(synth,-1,interpolation) }
    fun setReverb(roomSize:Double,damping:Double,width:Double,level:Double) {
        jni.setReverbRoomSize(synth,roomSize);jni.setReverbDamp(synth,damping)
        jni.setReverbWidth(synth,width);jni.setReverbLevel(synth,level)
    }
    fun setChorus(voiceCount:Int,level:Double,speed:Double,depth:Double) {
        jni.setChorusNr(synth,voiceCount);jni.setChorusLevel(synth,level)
        jni.setChorusSpeed(synth,speed);jni.setChorusDepth(synth,depth)
    }
    fun renderFloat(frames:Int):FloatArray = jni.renderFloat(synth,frames)
    override fun close() {
        if(synth!=0L){jni.deleteSynth(synth);synth=0}
        if(settings!=0L){jni.deleteSettings(settings);settings=0}
    }
}
