# Stage MIDI audio correction, build 19

This investigation followed the report of rumbling/garbled Stage MIDI sound.
The previous gain/peak protection remains. This update also repairs musical
information lost between the MIDI file and FluidSynth. It does not claim that
the user's specific sound has been reproduced on a phone or TV.

## Confirmed source defects

1. KaraokeMidiParser retained notes and programs but discarded MIDI CC events,
   pitch bend, channel pressure and key pressure. Song-authored volume,
   expression, pan, sustain, bank selection, controller resets, effect sends and
   all-notes/all-sounds-off instructions never reached the synth. This can change
   the arrangement's balance, pitch and articulation considerably. A limiter
   alone cannot repair these omissions.
2. Group mixer faders scaled note velocity, altering velocity-sensitive samples
   and their timbre rather than simply mixing their output levels.
3. Seeking restored programs alone. Restoring the new controller support without
   also restoring controller state on seek would leave the wrong volume, bend,
   bank, expression or pedal state after seeking.
4. The existing tracked-note silence path did not include voices held by a sustain
   pedal after note-off. Once sustain is correctly supported, those voices must
   also stop on pause, seek, transpose and melody mute.

The earlier excessive-gain and cast hard-clipping defects were also reproduced
with actual desktop synthesis, as detailed below. These are confirmed defects
and demonstrated distortion mechanisms; no affected user song/font was available
to establish which is responsible for the exact reported sound.

## Changes

- Retain and dispatch CC, 14-bit pitch bend, channel pressure and key pressure in
  original MIDI order, including running-status events.
- Use JNA 5.15.0's Android AAR to call the existing bundled FluidSynth's public C
  MIDI APIs omitted by fluidsynth-kmp 1.1.0. Notes, rendering, SoundFont loading
  and effects still use the pinned JNI wrapper. No second synth, player or audio
  driver is created. The existing synthLock serializes all operations.
- Preserve authored note velocities. Apply group faders to the song's 14-bit
  CC7/CC39 channel volume while leaving expression independent. Live faders now
  affect held notes as well. Restore authored volume when a fader returns to unity.
- Reset MIDI state when loading songs and replay non-note channel-state events
  in original order before a seek target. Do not prematurely apply a program
  change from later in the song to its opening notes.
- Use all-sounds-off to stop tracked and pedal-held voices on transport changes,
  transpose and melody mute. The next song starts from clean controller state.
- Retain conservative master-gain calibration, stereo peak protection and its
  lyric latency compensation. No EQ, bass boost, new reverb/chorus or loudness
  enhancement is added. No changes to UI, library, scoring or lyric parsing.
- Package only the three ABIs with complete FluidSynth JNI implementations:
  arm64-v8a, armeabi-v7a and x86_64. Include JNA licensing notices.

## Verification

- Gradle testDebugUnitTest and assembleDebug succeeded. All 48 tests in 10 suites
  passed. New tests verify MIDI control/bend/pressure ordering, running status,
  original velocities, independent channel mixing, 14-bit volume and fader reset.
- Ran the production FluidMidiControls class against actual desktop FluidSynth
  2.5.2 using the repository's procedural sine SF2. Verified audible volume and
  expression attenuation, sustain, pitch-bend frequency change, pressure API
  dispatch, held-note silence and controller reset.
- Rendered three generated MIDI arrangements through the production MIDI parser
  and real FluidSynth: sparse voice, sustained chord, and dense arrangement. The
  comparison reproduces the old omitted-controller/gain path and the corrected
  path with effects disabled to isolate the fault. These are procedural tests,
  not the user's songs or a listening comparison at matched loudness.

| Generated fixture | Old raw peak | Corrected raw peak | Old samples above full scale | Corrected samples above full scale |
| --- | ---: | ---: | ---: | ---: |
| Sparse voice | 0.121462 | 0.018185 | 0 | 0 |
| Sustained chord | 0.948852 | 0.125852 | 0 | 0 |
| Dense arrangement | 4.540228 | 0.815490 | 55,900 | 0 |

Full scale is 1.0. The dense old output would hard-clip during PCM16 conversion.
The corrected output remained below full scale even before the retained guard.
Different sound banks may produce different peaks and still require protection.
Results and before/after WAVs are in audio-quality-v2-validation.

- Static APK/ELF verification passed: all six required FluidSynth C functions are
  defined exports for all three packaged ABIs; both synth JNI and JNA dispatch
  are present for each. This is not a substitute for Android loader execution.
- Existing two-hour browser scheduling simulation again passed with zero
  underruns/missing blocks and one expected revision-change flush.

## Installation and remaining verification

JKTV-v0.4.4-stage-audio-test2.apk uses com.jktv.offline.cast.audio, versionCode 19,
versionName 0.4.4-audio-test2. It updates the previous audio-test app signed on
this machine and retains that app's library. If only the original casting-parallel
app is installed, this test app installs separately and needs its own library
setup. The original app is preserved. For a native Android TV receiver, both
sender and receiver need the changed engine. Browser Stage receives corrected
PCM from the host and needs no receiver APK update.

A direct update to the original com.jktv.offline.cast APK still requires its
original signing key. The available debug key's certificate differs from the
original. Do not uninstall the original app to work around the signing mismatch.

No Android device was connected and the local songs/SoundFont folders remain
empty. Android runtime loading, actual affected songs/fonts, speaker/headphone/
Bluetooth output, native TV and real-network listening remain unverified. The
desktop runtime is 2.5.2; the unchanged packaged Android FluidSynth is 2.5.3.
Manufacturer SysEx/XG/GS extensions remain unsupported by this parser; this
update restores standard MIDI channel messages, not every vendor extension.
Existing source distortion, poor samples, physical speaker overload and output
device latency cannot be repaired or certified by these code tests.

Sources: FluidSynth MIDI API https://www.fluidsynth.org/api/group__midi__messages.html
and JNA Android packaging https://github.com/java-native-access/jna/blob/master/www/FrequentlyAskedQuestions.md#jna-on-android

Rebuild using the installed Android SDK and Java 17:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug '-PjktvApplicationId=com.jktv.offline.cast.audio' '-PversionName=0.4.4-audio-test2' '-PversionCode=19'
```

Reproduce native QA with the official FluidSynth 2.5.2 Windows binary and JNA
5.15.0 desktop JAR using tools/NativeMidiAudioQa.java. This harness requires the
compiled app Kotlin classes and Kotlin stdlib on its classpath. Validate packaged
ABIs with `python tools/verify_midi_bridge_abi.py path-to-apk`.
