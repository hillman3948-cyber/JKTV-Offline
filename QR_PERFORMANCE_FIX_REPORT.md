# JKTV Offline Pro v0.4.4 — QR and playback performance fixes

Local implementation and validation, October 27, 2026. No upload, release, version bump, or changes to older APK versions.

## What changed

The TV has a locally generated top-right QR labeled **SCAN TO CONNECT**, with a white quiet zone and a translucent rounded card. Its guest URL opens Browser Remote directly. The host controls visibility in **Settings > Lyrics & Sync > Stage Elements > Show TV guest QR code**. The option persists and synchronizes through the existing Stage configuration. The QR refreshes from the active LAN address/session. Sharing it grants the existing remote controls; guests cannot claim Stage audio or obtain the host pairing PIN from their page. Disabling Wi-Fi Remote ends the session; restarting rotates its guest token.

The host still owns one synth, playback clock, queue and Stage settings. Browser Stage receives that synth's PCM; it does not run a second MIDI player. Audio ownership remains exclusive and expires after a lost connection. Temporary reconnections use the existing session without reloading Stage. Host menus do not switch the TV out of Stage.

## Diagnosed causes and fixes

- The old audio buffer selected the last 32 unread blocks, dropping older unread samples after a delay. It now sends the oldest unread blocks in bounded batches and keeps about six seconds of host history.
- The old client cleared audio when the queue grew and could clear it twice for one transport revision. Queue feedback now provides backpressure, tiny rate corrections preserve samples, and each new transport revision is applied once. An adaptive buffer handles ordinary jitter without restarting the MIDI song.
- Repeated audio HTTP requests and per-block Web Audio source creation added overhead. A persistent authenticated WebSocket carries PCM and incremental Stage updates. A Worker converts PCM away from lyric rendering, and the client schedules merged audio batches. HTTP remains a compatibility fallback.
- Slow Stage snapshot generation could hold up audio. Snapshot jobs are independent of PCM delivery. Native audio rendering uses a dedicated dispatcher rather than the shared network/database pool.
- Lyrics repeatedly measured and rasterized unchanged text. Font fitting is cached per song/settings/viewport; cached Canvas glyphs are clipped for the syllable wipe. Unchanged elements avoid DOM updates. Visualizer resolution/cadence adapts under load, targeting 60 fps when capable.
- Browser Remote search/status polling continued while Stage was displayed. It now runs only in Remote mode; searches are debounced and stale results discarded. Queue DOM changes only when the queue changes. Request IDs and pending-action guards prevent duplicate submissions.
- Large HTTP responses could be truncated on connection close in the Android test transport. Responses now write headers/body together, half-close output and briefly allow the peer to finish before socket release. Large HTTP Stage snapshots use gzip when the client accepts it, reducing initial-transfer traffic; ordinary updates remain incremental.
- A foreground media-playback service holds bounded CPU/Wi-Fi locks during an active session, renewed by its live owner. It stops when the session ends and does not reconstruct or restart a song after process death.

## Validation

Completed checks: 39 JVM tests passed, including independent QR decoding and WebSocket framing; 100 browser checks passed across settings, lyric pixels, reconnection, backgrounds and 720p/1080p/4K/ultrawide layouts. Android lint reported 0 errors and 87 warnings. A two-hour simulated audio scheduler run had zero underruns/missing blocks, with bounded queues. Thirty complete compressed HTTP snapshots passed delivery/JSON checks. The live Android/Chrome suite passed all 12 cases, including 600 seconds of continuous synthesized audio with three guest remotes searching a 50,000-song Room catalog. Across 584 samples: zero underruns, missing blocks or transport resets; at most 9 scheduled audio sources, 243 timeline segments and 0.398 seconds queued. Measured main-browser heap decreased from 3,355,988 to 3,206,692 bytes after garbage collection. Android PSS changed from 196,154 to 202,561 KB. Forced interruption restored host audio and automatic reconnection preserved Stage/session. The separate HTTP compatibility suite passed all 11 cases, including pause, resume, seek, exclusive handoff, reconnect and unsupported-audio fallback. Direct Android TCP delivered all 30 uncompressed 400 KB responses intact (HttpDeliveryDeviceTest). Reproducible scripts are in `tools/`; test artifacts are in `performance-validation/` and `stage-sync-validation/`.

## Practical limits

These checks use an Android emulator with the real FluidSynth engine, server and Room database, plus desktop Chrome. No physical Samsung TV, phone microphone/speaker latency measurement, or overnight hardware run was available. Passing these tests is not certification that every TV browser supports Web Audio, Workers, video codecs or fullscreen equally. The UI preserves host audio and offers an activation button if remote audio cannot start.

A raw desktop diagnostic observed occasional truncated large (~399 KB) uncompressed replies through ADB forwarding. A separate direct Android TCP test then delivered all 30 uncompressed 400 KB responses intact; all 30 compressed browser-path responses also passed. The forwarding diagnostic is retained in performance-validation/http-identity-delivery.json. Physical LAN/Samsung validation remains required before commercial certification.

A finite buffer cannot hide an arbitrary Wi-Fi outage. After the exclusive lease expires, host audio returns; reconnection resumes from the current host timeline. If the host's IP becomes unreachable after a router address change, reopen the new pairing URL shown by the host. The QR updates whenever the receiver can receive the updated host configuration. Android force-stop, shutdown and manufacturer power restrictions may still end a session.

This build retains the existing voice-announcement limitation documented in TV_AUDIO_WIPE_FIX_REPORT.md; it does not add remote microphone or TTS streaming.

## Source and recovery

Edit browser sources in `app/src/main/stage-src/`, then run `node tools/build_stage_assets.cjs` before Gradle. Generated browser assets target Chromium 56 syntax; this does not imply hardware certification for Chromium 56-era TVs. ZXing is a unit-test dependency only, used to decode the generated QR independently.

The pre-change source/APK backup is `qr-performance-v044-backup/`. The delivered APK remains `JKTV-v0.4.4-casting-parallel.apk`, package `com.jktv.offline.cast`, version 0.4.4 / build 17. It updates the existing casting test installation.

Delivered APK SHA-256: 592cca3873e21585c086b790bf3528615bdb5ced30b03219710c57fa1bf5fd11
