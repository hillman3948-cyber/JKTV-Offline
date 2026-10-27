package com.jktv.offline.audio

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/** Calls the public C ABI of the SAME bundled synth; never creates a second player/driver.
 * All calls are serialized by the engine's existing synthLock. PCM rendering stays on JNI.
 */
class FluidMidiControls(library: NativeLibrary = NativeLibrary.getInstance("fluidsynth")) {
    private val cc = library.getFunction("fluid_synth_cc")
    private val bend = library.getFunction("fluid_synth_pitch_bend")
    private val pressure = library.getFunction("fluid_synth_channel_pressure")
    private val keyPressure = library.getFunction("fluid_synth_key_pressure")
    private val reset = library.getFunction("fluid_synth_system_reset")
    private val silence = library.getFunction("fluid_synth_all_sounds_off")

    fun control(synth: Long, channel: Int, number: Int, value: Int) {
        cc.invokeInt(arrayOf(Pointer(synth), channel, number, value))
    }
    fun pitchBend(synth: Long, channel: Int, value: Int) {
        bend.invokeInt(arrayOf(Pointer(synth), channel, value))
    }
    fun channelPressure(synth: Long, channel: Int, value: Int) {
        pressure.invokeInt(arrayOf(Pointer(synth), channel, value))
    }
    fun keyPressure(synth: Long, channel: Int, key: Int, value: Int) {
        keyPressure.invokeInt(arrayOf(Pointer(synth), channel, key, value))
    }
    fun reset(synth: Long) { reset.invokeInt(arrayOf(Pointer(synth))) }
    fun silence(synth: Long, channel: Int) { silence.invokeInt(arrayOf(Pointer(synth), channel)) }
}
