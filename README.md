# JKTV Offline Karaoke

JKTV is an Android karaoke player designed to download a MIDI/KAR library and a SoundFont once, verify them, then play them offline. The Android project includes Compose screens, a Room catalog and singer queue, a resumable HTTPS asset installer, incremental package handling, SHA-256 validation, and GitHub release automation.

## Current state

- The project builds a debug APK. The APK is a development build, not a production-signed release.
- The Desktop asset folder currently contains 47,997 `.mid` files (about 2.16 GB) and four `.sf2` SoundFonts (about 494 MB). Header checks passed; they do not prove every file is fully parseable.
- The public GitHub repository is `hillman3948-cyber/JKTV-Offline`. Karaoke assets are published as release downloads, not committed into Git history.
- The home screen can check for signed APK and library releases. APKs are SHA-256 checked before Android's installer opens; library packages have resumable downloads, SHA-256 checks, and staged installation.
- MIDI/KAR playback is powered on-device by FluidSynth using the installed SF2 SoundFont. Song playback, pause, seek, and timed MIDI lyrics work offline after library setup.
- Synced lyrics can be embedded in MIDI/KAR files or supplied as a same-name `.lrc` sidecar (for example, `Song.mid` plus `Song.lrc`). The library packager includes sidecars in song archives, and an LRC sidecar overrides embedded lyrics.
- Settings include a persistent MIDI master-volume, reverb, and chorus mix, selectable Violet/Ocean/Emerald appearance themes, SoundFont selection, and looping background-video selection.
- The song browser renders rows lazily, pages through large libraries, debounces rapid search input, and includes a song-number keypad. The singer queue can skip or automatically start the next valid queued song when playback completes.
- FluidSynth and wrapper license texts are bundled in the app under `assets/licenses/`.
- Production in-place APK updates require one stable release keystore. The workflow expects its credentials as GitHub Actions secrets and will not publish a tagged APK without them.

## GitHub release layout

The repository contains Android source, workflow files, manifests, documentation, and developer tooling. Karaoke files are excluded from Git and hosted in the Cloudflare R2 bucket `jktv-offline-library`; the Android source and APK releases remain on GitHub. The app reads the library manifest from Cloudflare R2 at `https://pub-b98e45182dd74f2d84728bc81bad6809.r2.dev/library-manifest.json`; the URL can be overridden with `jktvLibraryManifestUrl` during the build.

See [DEPLOYMENT.md](DEPLOYMENT.md) for GitHub release and Android build instructions. The repository is public so the app can download assets without embedded credentials. The owner confirmed permission to redistribute these songs and SoundFonts.

## Build locally

Open this directory in Android Studio or use Gradle 8.11.1 with Java 17 and Android SDK Platform 35:

```powershell
gradle --offline --no-daemon :app:assembleDebug
```

The debug output is `app/build/outputs/apk/debug/app-debug.apk`. CI builds and attaches the APK as a temporary workflow artifact. A signed release is produced only from an `app-vX.Y.Z` tag after the signing secrets are configured.
