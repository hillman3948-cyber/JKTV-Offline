# Native casting implementation — app-v0.4.4

Implementation date: 2026-10-26 (Asia/Taipei).

The source changes are in `C:\Users\hillm\OneDrive\Desktop\jkt-apk`. This is a native receiver implementation with automated and emulator validation, **not a hardware-qualified release or confirmation that every requested acceptance test passes**. No GitHub upload, push, public APK, release, or version increment was made. Existing root APKs and `app-version.json` were not replaced.

## Implemented changes

- Compact Cast button, accessible labels, 48dp header controls, responsive action labels, and status-bar clearance.
- Searching, Connecting, Connected, Reconnecting, and Disconnected states; device list; manual address fallback; pairing authorization; Retry and Disconnect.
- Dedicated Android TV/Google TV launcher and receiver Activity. The receiver never instantiates the phone navigation UI.
- Local NSD discovery, encrypted persistent command transport, media content hashes, bounded receiver cache, and independent native Stage rendering.
- Reused FluidSynth playback, resolved syllable lyrics, lyric styles, custom colors, duet labels, visualizers, tempo, transpose, melody controls, and mixer settings.
- Optional TV MIDI audio with phone synth muting; phone audio mode with a silent receiver; saved lyric compensation.
- Monotonic timestamp extrapolation, bounded transit compensation, drift correction, reconnect backoff, audio-lease expiry, and resource cleanup.
- Muted, aspect-preserving center-cropped receiver video, synchronized to the current song position.

## Architecture

Phone `KaraokeViewModel` owns the existing library, queue, settings, song transitions, and authoritative MIDI clock. `CastController` captures immutable Stage snapshots on the main dispatcher and performs network, hashing, and media IO on background dispatchers.

`CastDiscovery` advertises/browses `_jktv-stage._tcp.`. It serializes older Android NSD resolutions, removes lost devices, and discards stale discovery callbacks. Manual address/port entry is available when a router blocks discovery.

`CastWire` uses length-bounded JSON frames over TCP. A random 128-bit pairing secret is shown on the TV and entered on the phone. Independent 32-byte handshake nonces derive a session key using HMAC-SHA256; direction-specific monotonic nonces protect AES-GCM frames. Secrets are not sent over the network or saved in preferences. New connections derive new keys. This is a custom authenticated protocol, not Google Cast, WebSocket, TLS, or screen mirroring.

`TvReceiver` accepts one connection at a time. Assets are SHA-256-addressed, transferred in 64 KiB chunks, verified before committing, and never addressed using remote filesystem paths. Individual assets are limited to 1 GiB; reusable receiver storage is limited to 2 GiB. Current Stage assets are protected from cache eviction. Settings and lyrics are sent when changed; playback ticks are sent approximately every 50 ms plus network/processing time.

## Supported devices and installation

The implemented target is Android TV or Google TV with Android 8/API 26 or newer and the compatible JKTV application installed. Native library ABI and media codec support must still be checked on each TV. Install the same locally built app on the phone and TV; the TV launcher entry is **JKTV TV Stage**. Open that entry, put both devices on the same LAN, then select the TV from the phone's Cast dialog and enter its displayed pairing key. Keep the receiver open while casting.

Generic Chromecast/Google Cast receivers are **not implemented**. Samsung Tizen, LG webOS, and other non-Android TVs cannot use this receiver directly. An attached compatible Android TV/Google TV device is the supported architecture for such displays; actual device compatibility remains unverified.

No release APK was published. A local debug APK was generated only for emulator QA. Debug signing may prevent installation over an existing differently signed release; do not uninstall a user's existing app to work around that without preserving its data.

## Full-screen Stage

`TvReceiverActivity` requests immersive system-bar hiding on creation and focus regain, keeps the display awake while visible, and renders only the Stage after connection. There are no Search, Queue, Settings, phone navigation, or transport buttons on the TV. The title and lyrics sit inside proportional 5% horizontal/vertical safe margins; the background covers the whole display. The existing syllable renderer and procedural visualizer are reused. The receiver hides the interactive interlude-skip button.

`TvVideo` uses a hardware-backed TextureView and decoded video dimensions to center-crop without stretching. It mutes background audio, tracks song position modulo video length, pauses with playback, and releases its player/surface when removed. System UI may still be revealed by platform gestures, as Android permits.

## Audio and lyric synchronization

The phone's sequencer remains authoritative. Ticks contain position, tempo, play state, lyric offset, channel activity, and estimated one-way transit time. Receiver processing time is excluded from the measured round trip. Transit compensation is capped at 250 ms.

In TV audio mode, the receiver synthesizes the transferred MIDI with the selected SoundFont, and its local audio clock drives lyrics. Clock differences over 150 ms, paused seeks, or newly enabled TV audio cause correction. Normal smaller jitter does not continuously seek. The phone synth is muted before granting TV audio, and restored after the receiver acknowledges phone mode or its audio lease expires. TV mode excludes phone-specific Bluetooth calibration.

In phone audio mode, the receiver extrapolates Stage timestamps locally and stays silent. User lyric compensation is adjustable between -2000 and +2000 ms and persisted. Resolved lyric lines/syllables and their source/confidence are transferred, including custom sidecar timing; the receiver does not regenerate approximate lyrics when authoritative timing exists.

This bounds drift; it does not establish sample-accurate synchronization or measured end-to-end acoustic latency. Background video seeks use a 300 ms tolerance and minimum 500 ms interval. Real hardware/audio validation is still required.

## Offline behavior and reconnect

The casting path contains no cloud requests. Receiver discovery, pairing, commands, and assets use the local network. The pre-existing library installation/update features remain separate and can still need internet access. Fully offline use requires the compatible receiver already installed and the phone's media available locally.

The phone preserves its existing queue/song/settings and retries interrupted connections with controlled delays, up to six attempts. A receiver audio lease expires after two seconds without ticks. The Stage remains displayed during interruption, while audio pauses. A successful reconnect sends a fresh snapshot and resumes at the current authoritative position; it does not deliberately restart the song. Manual disconnect stops the receiver session and restores phone audio. Leaving the receiver Activity closes sockets, unregisters discovery, and releases the engine after pending native work exits.

## Verification results

- Java 17 compile and local `assembleDebug`: passed.
- Unit tests: **18 passed, 0 failures** — 9 existing lyric-sync tests plus 9 casting tests.
- Casting tests cover encrypted ordered exchange, Unicode, incorrect keys, oversized frames, key validation, content hashes, custom lyric round-trip, Stage-only data boundaries, bounded transit compensation, pause handling, and seek thresholds.
- Android lint: **0 errors, 81 warnings**. Warnings remain; this is not a warning-free project.
- Receiver launch and phone Cast-dialog launch on the installed API 37 phone emulator: passed; no recorded AndroidRuntime crash during these checks.
- Synthetic encrypted receiver session, centered lyrics, immersive Stage, reconnect, and disconnect: passed separately at 1280×720, 1920×1080, and 3840×2160 emulator display configurations.
- Mean synthetic ADB-forward round trips: **3.36 ms (720p, 22 ticks), 4.08 ms (1080p, 23 ticks), 9.37 ms (4K, 34 ticks)**. These are development-host/emulator observations, **not Wi-Fi or audio latency benchmarks**.
- Screenshots and machine-readable smoke results are in `casting-validation/`. The reusable probe is `tools/cast_receiver_smoke.py`; it requires Python `cryptography` and ADB and uses synthetic lyrics with no audible media.

The first resize-during-connection smoke run failed because the emulator restarted the Activity. It was not counted as a pass. The probe now sets each resolution before launching and pairing. Visual review also found and fixed pairing-text contrast and phone status-bar overlap.

## Unverified behavior and remaining improvements

1. No physical phone/TV pair was available. NSD across real Wi-Fi, internet-disconnected LAN playback, router isolation, actual Wi-Fi interruption/recovery, DHCP address changes, real MIDI/SF2 TV audio, video codecs, audio handover, long sessions, CPU/memory/FPS, discovery/connection/reconnect timings, and acoustic drift are **not hardware-tested**.
2. Cold media is copied in full before use over the command connection. Large uncached SoundFonts/videos can delay readiness; updates/audio on the TV can pause while media is prepared, while the phone clock continues. The receiver catches up afterward. Seamless cold-cache song/background transitions require prefetching or a separate media channel and coordinated buffering.
3. Background replacement releases the previous player before preparing the next. Gapless video crossfades are not implemented.
4. Automatic retry uses the selected endpoint. If the TV address/port changes or the receiver restarts with a new pairing key, rediscover and pair again. Persistent remembered-device trust and QR-camera pairing are not implemented.
5. Keep the phone app and receiver foreground. No casting foreground service/wake-lock architecture was added for prolonged locked-screen or background-phone operation.
6. Native audio remains on both devices internally to preserve the authoritative phone sequencer; phone output is muted in TV mode. Voice announcements/scoring are still existing phone features and are not routed to the TV.
7. Generic Google Cast/custom web receivers are not implemented. The UI states this limitation explicitly.
8. Overscan on physical TVs, unusual aspect ratios, real video aspect ratios, all 25 visualizers, large accessibility font settings, and a complete media/queue regression matrix remain acceptance work. Emulated 16:9 screenshots do not establish those results.
9. Receiver cache controls, background export-cache pruning during long sessions, progress percentages, advanced smooth drift adjustment, security review of the custom protocol, and production hardware performance tuning remain improvements.

## Complete source/configuration change list

Modified existing files:

- `app/build.gradle.kts` — source defaults aligned to the supplied 0.4.4/build 17 metadata; real JSON dependency for JVM tests only.
- `app/src/main/AndroidManifest.xml` — dedicated leanback receiver launcher/activity and banner.
- `app/src/main/java/com/jktv/offline/MainActivity.kt` — Cast UI, responsive header targets, status-bar inset.
- `app/src/main/java/com/jktv/offline/KaraokeViewModel.kt` — controller ownership and snapshot/cleanup integration.
- `app/src/main/java/com/jktv/offline/playback/MidiEngine.kt` — output muting independent of the authoritative clock and access to the active SoundFont.
- `local.properties` — Android SDK location corrected from the unavailable Administrator path to this machine's installed SDK.

New files:

- `app/src/main/java/com/jktv/offline/cast/CastController.kt`
- `app/src/main/java/com/jktv/offline/cast/CastControls.kt`
- `app/src/main/java/com/jktv/offline/cast/CastDiscovery.kt`
- `app/src/main/java/com/jktv/offline/cast/CastWire.kt`
- `app/src/main/java/com/jktv/offline/cast/StageClock.kt`
- `app/src/main/java/com/jktv/offline/cast/StageCodec.kt`
- `app/src/main/java/com/jktv/offline/cast/TvReceiver.kt`
- `app/src/main/java/com/jktv/offline/cast/TvReceiverActivity.kt`
- `app/src/main/java/com/jktv/offline/cast/TvVideo.kt`
- `app/src/main/res/drawable/jktv_tv_banner.xml`
- `app/src/test/java/com/jktv/offline/cast/CastWireTest.kt`
- `app/src/test/java/com/jktv/offline/cast/StageClockTest.kt`
- `app/src/test/java/com/jktv/offline/cast/StageCodecTest.kt`
- `tools/cast_receiver_smoke.py`
- `CASTING_IMPLEMENTATION.md`

Original versions of the six modified files are preserved under `casting-v044-backup/`. `casting-validation/` contains generated QA evidence rather than application source. Build outputs remain in the project's configured temporary Gradle output directory. No existing root release APK was overwritten.

Build environment: installed Microsoft JDK 17. Java 25 was incompatible with this Gradle setup. Maven downloads required Java's Windows-ROOT certificate store; TLS verification was not disabled. The validation command used `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE`.

Platform references used: [Android NSD](https://developer.android.com/reference/android/net/nsd/NsdManager) and [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive).

## Browser TV Stage and phone remote addendum

The existing QR link now opens a TV mode selector with **Show Karaoke Stage** and **Use Browser Remote**. Stage mode requests the browser Fullscreen API directly from the click gesture, then hides the QR remote UI. The native phone app continues controlling playback, songs, queue, and settings without navigation mirroring. Selecting browser Remote preserves the previous search, reserve, and playback controls. Escape or browser Back exits full screen and returns to the selector. A full-screen request is subject to the TV browser's permission and support.

`/api/stage` sends timestamped playback/lyric frames and sends syllable timing only when the song or lyric revision changes. The browser renders a safe-area Stage, timed syllable color wipe, countdown, animated bars, current singer/title, and selected lyric styling. `/stage-media` streams the selected phone background with HTTP byte-range support so TV browsers can seek. The receiver is the user's browser, not the phone screen.

This specifically addresses the requested QR workflow. TV browser support for fullscreen, H.264 profile, byte-range seeking, and local HTTP varies by model and has not been tested on the user's physical TV. The browser consumes the centralized host settings and lyric timeline through a Canvas renderer. Show Karaoke Stage now claims exclusive host-PCM audio; see TV_AUDIO_WIPE_FIX_REPORT.md for current behavior and hardware-test limits.

Additional modified file: `app/src/main/java/com/jktv/offline/remote/WifiRemoteServer.kt`.
Additional companion artifact: `TV_CASTING_QUICK_START.txt`.
The rebuilt separate-install APK remains `JKTV-v0.4.4-casting-parallel.apk` with application ID `com.jktv.offline.cast` and version 0.4.4/build 17.


## Stage synchronization/appearance follow-up
See STAGE_SYNC_FIX_REPORT.md for the current shared Stage snapshot, clean Canvas lyrics and host-clock correction. The latest TV_AUDIO_WIPE_FIX_REPORT.md covers browser audio handoff, 33 JVM tests, 99 browser checks and 10 Android PCM-to-browser checks. The updated APK remains version 0.4.4/build 17.

