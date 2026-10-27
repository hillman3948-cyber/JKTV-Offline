# JKTV Offline Pro 0.4.4: TV wipe and Wi-Fi Stage audio

Updated locally on 2026-10-26. Version 0.4.4 / versionCode 17, package com.jktv.offline.cast.
APK: JKTV-v0.4.4-casting-parallel.apk in this folder. No upload, publication, tag, or new release.
Source and the previous APK were backed up in tv-audio-v044-backup before this work.

## Using the update

Install the parallel APK over the existing JKTV casting test app. Keep the host and TV on the same LAN, enable Wi-Fi Remote on the phone, and open the current QR address on the TV. Reload an already open TV page after updating the phone app. Choose Show Karaoke Stage with the TV's select button. That gesture requests fullscreen and unlocks Web Audio. Once PCM is buffered and the host grants the audio lease, the host becomes silent and the TV plays the song at its current position. An audio retry button appears if browser autoplay policy blocks sound. Unsupported audio leaves the host audible.

Opening Search, Queue or Settings in the host app does not navigate or exit the TV Stage. The latest Stage settings continue arriving from the phone. Closing/hiding the active browser Stage restores host audio. An abrupt lost connection restores the host after the 1.6 second lease plus 250 ms drain guard. The browser schedules its gain to reach zero before the lease expires. Choosing Stage on another screen explicitly transfers audio ownership; the older screen keeps its picture and cannot automatically steal audio back.

## Lyrics and shared state

Replaced browser SVG fragment/clipPath overlays with persistent Canvas 2D lyric surfaces and real rectangular clipping. This avoids reliance on SVG fragment clipping in TV engines. The reported Samsung symptom is addressed by removing that dependency; the original failure has not been reproduced on physical Samsung hardware. UTF-16 character spans, spaces, word groups and MIDI syllable start/end times come from the same StageCodec/StageTimeline data as the Android Stage. Highlight widths change within each timed syllable. The base text stays visible outside the wipe.

The host remains authoritative for settings, song identity, transport revisions, play/pause, tempo, position and lyric calibration. The browser does not play MIDI, synthesize a second song, or store separate Stage preferences. Canvas text uses the host font, weight, scale, alignment, palette, outline and shadow settings. Row geometry stays stable for countdown changes and ordinary clock ticks. Source files are in app/src/main/stage-src; generated browser assets are in app/src/main/assets/stage.

Active audio receivers display lyrics using their scheduled PCM playout position and the host's lyric calibration. Measured presentation latency feeds the host snapshot so other screens follow that output. Small PCM timestamp corrections are smoothed, buffer gaps hold the lyric endpoint, and the wipe cannot move backwards during ordinary playback. Seeks, song changes and transport revisions explicitly reset the audio queue. Reconnect retrieves the current configuration and position without restarting the song or reloading the page.

## Single audio output

FluidSynth now renders once into float PCM. That exact stream feeds Android AudioTrack and the authenticated browser PCM endpoint. Host muting changes AudioTrack volume, preserving the synth, MIDI sequencer, effects, key, tempo and PCM stream. The driverless facade uses the pinned fluidsynth-kmp 1.1.0 JNI interface; its internal ABI and the current Kotlin compiler are pinned dependencies. Run native instrumentation before upgrading either.

The browser receives bounded stereo 44.1 kHz PCM16 packets, schedules Web Audio buffers, and renews a token-protected exclusive lease. Preparation does not mute the phone. Commit waits for buffered audio and the previous owner/native TV output to relinquish ownership. Delayed responses cannot grant playback beyond the safe deadline. Watchdogs run without an open Stage poll. The native Android TV audio selector revokes browser ownership before enabling native TV audio.

This transport carries synthesized karaoke music, including the host synth mixer/effects. It does not capture the microphone or arbitrary Android/system audio. Android voice announcements are silenced while a remote owns audio; local announcements remain available in host audio mode. Remote voice-announcement transport is not implemented. This limitation must be resolved or explicitly scoped before a commercial release that promises remote announcements.

## Compatibility

Stage, audio and browser-remote JavaScript are compiled to Chromium 56 syntax with pinned esbuild 0.25.11. A DOM replacement fallback, fetch timeouts without AbortController, flex-gap fallback, fullscreen prefixes, and CSS sizing fallbacks reduce dependence on newer browser features. Generated assets are included with the source; Gradle needs no Node or network to regenerate them. To edit JavaScript: npm ci --prefix tools/stage-build, then node tools/build_stage_assets.cjs. The Android build continues to work with JDK 17.

This is a compatibility target, not proof that every Tizen 4+ TV or every device works. Samsung changes browser engines and media capabilities by model/year. Hardware autoplay, fullscreen policy, H.264/video profiles, speaker/soundbar/Bluetooth latency, memory pressure and Wi-Fi quality vary. Unsupported Web Audio keeps music on the host. The existing native receiver is available for compatible Android TVs. No universal-device or perfect acoustic synchronization claim is made.

References: [Samsung browser engine specifications](https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html), [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack), [Web Audio scheduling](https://www.w3.org/TR/webaudio-1.0/).

## Verification

- Build, lint and 33 JVM tests pass, including 8 new ownership/PCM tests: preparation, native wait, token validation, renewal, lease expiry, drain, takeover, release, revoked clients and bounded binary packets.
- 99 browser checks pass against actual app assets and Kotlin-generated snapshots. Coverage includes individual Stage settings, palettes, fonts, weights, visualizers, backgrounds, pause/seek/reconnect, resolution changes, stable countdown positioning and missing DOM/AbortController fallbacks.
- Pixel tests sampled three positions inside one syllable. Red highlighted pixels increased 235 -> 1296 -> 2008 while later syllables stayed unsung. Evidence: stage-sync-validation/wipe-pixels.json and wipe-1100/1400/1700.png.
- Android API 37 emulator instrumentation passes: real JNI SoundFont loading, nonzero synthesized PCM, continued PCM while AudioTrack is muted, and uninterrupted sequencer position.
- 10 browser/device checks pass through the real Android HTTP server: nonzero Web Audio signal, exclusive two-screen takeover, host restoration on close/network loss, reconnect, settings during audio, pause/resume/seek and unsupported-audio fallback. Samsung user-agent emulation was used in desktop Chrome; this is not a Samsung engine test.
- Forty playout samples were monotonic; maximum difference from the compensated host display clock was 61.0 ms in that run. This is a development test result, not an acoustic latency guarantee. Evidence: audio-stage-validation/audio-clock-samples.json, device-browser-results.json, instrumentation-results.txt and android-pcm-stage.png.

Physical Samsung TV, phone/tablet browsers, soundbar paths, sustained long sessions and real LAN loss/jitter were not available for hardware acceptance. The APK is a local debug test build and is not commercially certified. Use a supported-device matrix and real hardware acceptance before commercial release.

APK SHA-256: b0f8d02854cdbadb4b6ef645202f405ecbe268eec867425f649b4f6ce1efe725

