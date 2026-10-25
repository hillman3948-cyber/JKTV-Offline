# JKTV and KARA-OKAY feature comparison

Review date: 2026-10-25. This review compares the JKTV source in this workspace with the KARA-OKAY 1.6.0 archive supplied by the user.

## What was available to inspect

`KARA-OKAY-1.6.0.zip` contains `.gitignore`, `README.md`, and eight screenshots in `docs/`. It contains no Kotlin, Gradle, native source, tests, or license directory. Its README explicitly says KARA-OKAY's source is not public. The feature descriptions below therefore reflect the README and screenshots, not a source-level audit or verified behavior. No KARA-OKAY code or branded art was copied into JKTV.

## Existing JKTV architecture and behavior

- Native Android app built with Kotlin, Jetpack Compose/Material 3, Room with FTS4, coroutines, and `fluidsynth-kmp` 1.1.0.
- R2 HTTPS JSON manifest downloads song ZIPs, SF2 banks, and MP4 backgrounds. The installer checks package hashes and archive paths, stages library updates, resumes cached package downloads, and indexes songs in Room.
- MIDI/KAR playback uses FluidSynth and a locally installed SoundFont. Embedded MIDI/KAR lyric events and same-name `.lrc` sidecars are parsed for timed display.
- Five destinations: Home, Songs, Queue, Now Playing, and Settings. Songs support text search, favorites, singer assignment, and queue insertion. Queue items can be reordered or removed.
- Settings can select downloaded or phone-picked MP4 backgrounds and select/import SF2 SoundFonts. The current build also has persisted master/reverb/chorus controls and Violet/Ocean/Emerald themes.
- GitHub app updates verify the APK SHA-256. The Room database currently stores song metadata, favorites/play counts, and the singer queue.
- The project bundles FluidSynth and wrapper notices under `app/src/main/assets/licenses/`.

## Feature comparison

| KARA-OKAY README feature | JKTV at review | Action/status |
|---|---|---|
| Offline song, SoundFont, and video downloads | R2 manifest packages and offline storage already exist | Preserved. Install is currently package-complete rather than first-songs-first streaming. |
| More than 47,000 songs, categories, and five-digit numbers | Room catalog, FTS title/artist search, path-derived number when present | Search exists; dedicated category browsing and number keypad were missing. This update adds a number keypad. |
| Singing stage with moving backdrop and lyric timing | MP4 stage plus embedded MIDI/KAR and `.lrc` timing | Stage exists. This update groups timed syllable events into readable lines and highlights the active token. Actual rendering still depends on source lyrics carrying usable timing. |
| Reserve singers, skip, and automatically start next song | Singer queue, reorder/remove, play/pause/seek | Manual and automatic queue advance were missing. This update adds skip and play-next-on-completion. |
| Key and tempo adjustment; melody-guide toggle | Not exposed by the current FluidSynth MIDI-file wrapper | Not added: changing this safely requires MIDI transformation or a sequencer with corresponding controls. |
| Singing score and microphone test | No capture/pitch-scoring pipeline | Not added: this needs microphone permission, audio analysis, calibration, and device testing. |
| Instrument-by-instrument mixer | Current controls are master volume, reverb, and chorus | Partial mixer only. Per-channel/instrument faders need sequencer/MIDI-channel control; the wrapper used here does not expose that API. |
| Lyric editing and tap-to-time | Lyrics are read-only during playback | Not added in this implementation slice; safe edits need a sidecar editor and synchronization workflow. |
| User-owned MIDI/KAR/MP3 songs and karaoke videos | Users can pick an MP4 background and import SF2; the downloaded catalog is indexed | MIDI/KAR/MP3 import and general media catalog are still missing. MP3 playback/vocal removal would need a separate playback/DSP pipeline. |
| App theme and lyric appearance controls | Three app color palettes; lyric sizing/outline controls are absent | App themes are present. Per-lyric typography controls remain missing. |
| Printable PDF songbook | No PDF export | Missing. |
| Phone/TV remote, QR pairing, Android TV | No remote service or pairing flow | Missing; requires a network protocol and TV-focus navigation. |
| PC mixer, microphones, DJ decks/controllers, recording, second-screen TV | Android-only project | PC-specific features are outside this app's platform. |
| Speech search, accent-insensitive search, MP3 lyric lookup | Title/artist/number search, no speech or lyric service | Speech/online lyric lookup are absent. Offline use remains the design target. |

## Current implementation slice

This update focuses on features that fit the existing native architecture without replacing the Room catalog or downloaded library:

1. Display MIDI/KAR lyric events as complete lines and highlight the timed lyric token, with the next line queued visually.
2. Automatically consume the next valid queue item after a song completes; add an explicit Skip control.
3. Add an on-screen song-number keypad and debounce/cancel stale search requests to reduce unnecessary catalog queries while typing.

The work is additive to the existing database schema; no queue, favorites, installed library, or user preferences are intentionally cleared. A full parity implementation would still require separate engineering for channel mixing, transpose/tempo, pitch scoring, lyric editing, MP3, PDF export, folder imports, and TV remote support.

## Licensing and compatibility

The supplied KARA-OKAY ZIP has no source or license files, and its README says the source is not public. The review uses its README feature list as a product reference only. JKTV retains its existing FluidSynth-KMP/FluidSynth notices. No KARA-OKAY source code, fonts, screenshots, or logo assets are incorporated.

JKTV remains a native Android app (`minSdk 26`, `targetSdk 35`). The listed PC-only KARA-OKAY capabilities are not directly applicable. Microphone scoring, folder import, and TV remote functions also require permissions, accessibility work, protocol design, and real-device verification beyond the changes in this update.
