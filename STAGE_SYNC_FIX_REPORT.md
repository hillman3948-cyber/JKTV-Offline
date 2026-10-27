Current follow-up: see [TV_AUDIO_WIPE_FIX_REPORT.md](TV_AUDIO_WIPE_FIX_REPORT.md) for the latest Canvas renderer, exclusive browser audio and validation. The historical checks below predate that follow-up.

# JKTV Offline Pro 0.4.4 — Stage synchronization and appearance fixes

The updated source and APK are in C:\Users\hillm\OneDrive\Desktop\jkt-apk1.

APK: JKTV-v0.4.4-casting-parallel.apk  
Package: com.jktv.offline.cast  
Version: 0.4.4 / build 17  
No upload, publication, release, or version increment was performed.

## What changed

The host phone's LibraryUiState and FluidSynth playback state are the single source of configuration and timing. StageCodec now produces one complete Stage settings snapshot for both the browser and native TV receiver. The browser has no independent Stage preferences. Its layout adapts to the display while consuming the host's settings and timed words.

The previous browser renderer rebuilt its lyric HTML every animation frame and used transparent gradient text combined with heavy shadows. It ignored several received settings. It also accepted the last network position without accounting for transit or distinguishing seeks from drift.

The browser now uses persistent Canvas text surfaces and word clipping (superseding the earlier SVG implementation). It measures a song's lyrics once for a selected font/configuration/display size and keeps that size across line transitions. Source spaces and UTF-16 syllable character offsets are retained. An actual text fill and narrow outline replace the problematic transparent text effect. Shadows are limited to one soft neutral shadow and honor the host shadow setting. Countdown and song information occupy fixed areas outside the lyric rows. No remote controls are drawn over the Stage.

The phone's native lyric renderer also uses consistent song-level fitting, the shared active-line rule, four actual rows for Four Line mode, a fixed countdown area, softer shadows, and a true stroke outline. Word spacing and phrase grouping are resolved centrally without retiming the syllables.

## Complete configuration synchronization

The canonical snapshot includes:

- Lyrics visibility, syllable highlighting, countdown visibility, song information, and singer information.
- Font family, weight, size scale, letter spacing, line spacing, alignment, position, display mode, and phrase grouping.
- Palette selection, custom active/glow/unsung/next-line colors, outline thickness, shadow intensity, and rainbow selection.
- Background selection/revision, image/video kind, brightness, and lyric scrim opacity.
- Visualizer style, primary/secondary colors, sensitivity, speed, brightness, intensity preset, lyric safe zone, glow strength, and performance mode.
- Song/artist/singer/duet information, key, melody, and mixer settings.

Stage Elements switches are available in the phone's Lyrics & Sync settings. The phone background picker now accepts images as well as videos. Images and video use center-crop presentation. An incoming browser background is prepared before replacing the previous background. Unrelated settings changes do not reload the media element or song.

The browser exports the host system font where available, including its actual weight and variable-font capability. If a system font cannot be exported, the corresponding browser generic family is used. Typeface rasterization can differ across platforms; identical font pixels are not promised.

All 25 existing visualizer IDs are recognized by the browser renderer. Their settings and colors come from the host. The browser uses Canvas equivalents of the native procedural styles, with a lyric safe-zone mask and subdued bars. Native Compose and browser Canvas are platform renderers of the same configuration, not independent playback/settings owners; they do not produce guaranteed pixel-identical effects.

## Playback and reconnection

The host sends a sampled lyric position, raw song position, monotonic sample/send timestamps, tempo, play state, song identity, and explicit transport revision. The lyric position includes the phone's existing audio/lyric calibration.

A browser clock estimates the host monotonic offset using measured round trips and the least-delayed recent samples. Small drift is corrected with bounded clock-rate adjustment rather than resetting the displayed lyric position every packet. Explicit seeks, pause/resume changes, song changes, and reconnects align to the current host position. Late packets are rejected. Extrapolation stops after 1.5 seconds without a current host packet.

Only one poll is outstanding. The normal poll interval is 100 ms after completion. Settings and lyric arrays are sent only when their revision changes. Temporary failure preserves the full-screen Stage and requests the complete latest configuration and lyrics after recovery. Library, Queue, Home, and Settings navigation do not affect the receiver view.

Restarting the phone QR server generates a new pairing PIN. In that case, open the new phone pairing URL. Temporary network interruption while the same server/PIN remains active automatically reconnects.

The HTTP server now isolates aborted browser/media requests so a cancelled download does not stop acceptance of other clients. Background media supports byte-range reads. Font download retries handle transient network failures.

## Verification

- Android assembleDebug: passed.
- JVM tests: 25 passed, 0 failures.
- Android lint: 0 errors, 85 warnings. Warnings remain; this is not a warning-free project.
- Browser acceptance matrix: 97 checks passed in headless Chrome, using the actual phone HTML/assets and canonical snapshots generated by Kotlin.
- Matrix coverage: individual Stage fields, all 25 visualizer styles, 15 palettes, six font-family choices, seven weights, Canvas effects, persistent lyric DOM/font size, stale packet rejection, transit compensation, small-drift slewing, pause, seeking, stale-clock expiry, reconnection, background image/video switching, and avoiding media reload on typography changes.
- Resolution checks: 1280×720, 1920×1080, 3840×2160, and 2560×1080; lyric safe margins and unchanged lyric-container bounds when countdown appears.
- Live Android emulator → Chrome: 10 checks passed. All five phone visibility/highlighting switches were toggled OFF/ON through the actual phone UI and observed in the receiver. Library, Queue, and Home navigation retained Stage/fullscreen. Actual QR HTML, Stage API, font bytes, and weight descriptor were checked.
- Final APK package inspection records the APK hash and confirms that its packaged CSS/JavaScript exactly match the validated source. A fresh optional emulator UI smoke attempt could not locate the QR button during startup, so it was not counted as a pass. The preceding live Android-to-browser checks passed; the final changes after those checks were font transport compatibility/retry handling and Android ARGB palette conversion.
- Browser screenshots, JSON results, JVM XML results, canonical fixtures, and lint output are saved in stage-sync-validation/.

One generated WebM fixture initially failed to demux intermittently. The test was corrected to request explicit VP8 frames and validate its WebM header. The completed matrix passed with valid media. A transient live font download reset was reproduced; a complete repeat download succeeded, and font retries and server request isolation were added.

## What remains unverified

No physical phone/TV pair was connected during this work. Real Wi-Fi timing, actual TV browser/codec behavior, Bluetooth/soundbar/display latency, sustained MIDI/SF2 audio output, acoustic synchronization, and long-duration CPU/GPU/memory performance require hardware acceptance testing. The browser follows the calibrated host clock; zero physical audio/display latency is not claimed.

The browser Stage now supports exclusive host-PCM audio handoff. See TV_AUDIO_WIPE_FIX_REPORT.md for the latest implementation, validation and limitations. Android restrictions on prolonged locked-screen/background-phone operation are unchanged.

The browser controls its own fullscreen origin/ESC notification and system UI. The app requests fullscreen from the Stage button gesture and does not inject phone controls. It cannot suppress browser security notifications or guarantee fullscreen support in every TV browser.

## Files changed in this fix

Modified:
- app/src/main/java/com/jktv/offline/KaraokeViewModel.kt
- app/src/main/java/com/jktv/offline/MainActivity.kt
- app/src/main/java/com/jktv/offline/playback/MidiEngine.kt
- app/src/main/java/com/jktv/offline/remote/WifiRemoteServer.kt
- app/src/main/java/com/jktv/offline/ui/StageComponents.kt
- app/src/main/java/com/jktv/offline/cast/StageCodec.kt
- app/src/main/java/com/jktv/offline/cast/TvReceiverActivity.kt
- app/src/main/java/com/jktv/offline/cast/TvVideo.kt
- TV_CASTING_QUICK_START.txt
- CASTING_IMPLEMENTATION.md
- JKTV-v0.4.4-casting-parallel.apk

Added:
- app/src/main/java/com/jktv/offline/stage/StageTimeline.kt
- app/src/main/java/com/jktv/offline/stage/StageSnapshot.kt
- app/src/main/assets/stage/stage.css
- app/src/main/assets/stage/stage-client.js
- app/src/test/java/com/jktv/offline/stage/StageSnapshotTest.kt
- tools/test_stage_browser.cjs
- tools/test_stage_live.cjs
- JKTV-v0.4.4-casting-parallel.apk.sha256
- STAGE_SYNC_FIX_REPORT.md

Before-fix source/APK copies are in stage-sync-v044-backup/. Prior release APKs were not replaced.

## Install this update

Install JKTV-v0.4.4-casting-parallel.apk over the previous parallel casting app. Its package/signing identity and version remain compatible with that app; no uninstall is needed for the parallel app.

Enable the phone's QR web remote, then reopen its current pairing address on the TV and select Show Karaoke Stage. Reopen or refresh the TV page once after installing this update so the browser loads the new renderer; an already-open older page contains its old JavaScript.

