# JKTV Offline Karaoke — Existing Project Analysis

**Analysis date:** 2026-10-25  
**Source project:** `C:\xampp\htdocs\KARAOKE` (inspected read-only)  
**Android workspace:** `C:\Users\hillm\OneDrive\ドキュメント\ChatGPT\JKT Offiline`

## Executive summary

The source of truth is an existing Node.js/Express karaoke server backed by MySQL, with browser-side MIDI synthesis and a file-based song library. It provides reusable filename conventions, MIDI duration parsing, timed lyric extraction, fuzzy library search, SoundFont selection, and singer queue behavior. It is not an Android application and depends on server services, so those behaviors need native/offline equivalents rather than a WebView wrapper.

## Project structure and verified assets

- `src/`: Express API, MySQL access, authentication, queue/session services, search, lyrics, SoundFont and MIDI-library management.
- `public/tv/`: browser karaoke screen and `midi-player.js` Web MIDI synthesizer client.
- `public/controller/`: paired-phone browser controller.
- `songs/`: 1,496 `.mid` files totaling 75,643,593 bytes (about 72.1 MiB). The importer also recognizes `.midi` and `.kar`, but none were found in this directory during the scan.
- `SoundFont/`: four `.sf2` files totaling 494,079,872 bytes (about 471 MiB). The configured default is `KaraOke_King_v2.sf2` (98,645,536 bytes). Current server selection supports SF2; SF3 was not found.
- `database/schema.sql`: MySQL schema for songs, lyrics, TV sessions, playback state, singer queues, settings, and analytics. No Room/SQLite Android database implementation was found.
- `scripts/import-midi-library.js`: recursive MIDI importer and filename metadata parser.
- `apk/JKTV.apk`: an existing 3,665,653-byte APK artifact is present. Its relationship to the requested offline Android app has not been established from source inspection.
- `data/`: includes a media-trash area with old/deleted material. Its large files are not treated as current library assets.

No `AGENTS.md` was found in the source project. The workspace had no visible project files before this report. The existing source tree was not modified.

## Existing karaoke behavior

Song metadata is inferred from filenames using the convention `NNNN - Title - Artist`; the numeric prefix is removed, the first remaining segment becomes the title, and later segments become the artist. The server importer reads MIDI headers/events to calculate duration and inserts searchable rows into MySQL.

The browser client loads MIDI files and SF2 fonts through HTTP endpoints into `js-synthesizer` (a browser/WASM synthesizer). It parses MIDI tempo changes and lyric/text meta events into a timed lyric timeline. The player exposes load, play, pause, seek, volume, SoundFont selection, stop, and playback state. Real-time transpose and tempo-control APIs were not found in the exposed player API. The browser player is not a reusable Android-native engine.

The server supports LRC, VTT, SRT, and plain-text lyric parsing for stored lyric records. Embedded MIDI lyric events are parsed on the TV client. The queue and singer state are stored in MySQL and coordinated through the session/realtime server. Search combines MySQL full-text/prefix candidates with spelling-distance ranking and a generated word index.

## Android architecture proposal

- Jetpack Compose for the purple/orange responsive interface, setup flow, song library, queue, settings, and landscape lyrics screen.
- Room/SQLite for songs, favorites, history, local playlists, queue, asset versions, and indexing state; FTS-backed indexed queries with incremental scanning.
- An Android-native synthesizer bridge (FluidSynth via JNI/NDK, subject to dependency/license and device validation) for SF2 playback. Keep the playback engine behind a Kotlin interface so the UI and library do not depend on server code.
- A local MIDI parser for duration and embedded lyric events, reusing the established event/timing behavior while implementing it in Kotlin. Only display word/syllable highlighting where individual event timing supports it.
- App-specific storage for verified downloaded assets. Read a versioned HTTPS manifest, resume downloads when Range is supported, verify SHA-256, safely extract archives, then atomically activate a completed library version.
- A developer asset packager that reads only the active `songs/` and chosen `SoundFont/` assets, writes deterministic ZIP packages and hashes, and generates a manifest. Provide a DigitalOcean Spaces S3-compatible upload mode using environment credentials and an explicit upload switch; never place credentials in the APK.

No DigitalOcean endpoint, bucket, CDN URL, or credentials are configured in the checked-in example environment. The manifest and tool can be prepared with endpoint values supplied at deployment time; actual upload and a production APK download test cannot be claimed until the destination is configured.

## Porting constraints and next steps

The MySQL schema, Express APIs, Socket.IO session model, browser/WASM engine, web authentication, video upload features, YouTube integration, and server-side state cannot run as-is in a fully offline Android app. The Android project should port the data and user-facing karaoke behavior while keeping songs and SoundFonts as downloadable assets. Current asset counts also do not substantiate the brief's 48,000-song target; capacity can be designed for that scale, but the present library has 1,496 active-folder MIDI files.

Next implementation steps: create the Android app shell and local data models; implement manifest/download and safe package installation; build the offline index and queue; integrate native MIDI playback and timed lyrics; then build an APK and validate on an Android device. This workspace currently contains no Android SDK/Gradle project files, so build tooling availability still needs checking.
